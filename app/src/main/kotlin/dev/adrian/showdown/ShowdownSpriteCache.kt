package dev.adrian.showdown

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Movie
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min
import org.json.JSONObject

internal fun isGenericSpritePlaceholder(path: String): Boolean {
    val fileName = path.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
    return fileName.equals("substitute", ignoreCase = true) || fileName.equals("decoy", ignoreCase = true)
}

internal fun isHighResolutionSpritePath(path: String): Boolean =
    path.contains("/animados-gigante/", ignoreCase = true) ||
        path.contains("/animados-sinbordes-gigante/", ignoreCase = true)

internal fun requiresAnimatedSprite(path: String, animatedOnly: Boolean): Boolean =
    animatedOnly || isHighResolutionSpritePath(path)

internal fun hasMultipleGifFrames(bytes: ByteArray): Boolean {
    if (bytes.size < 13) return false
    val signature = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
    if (signature != "GIF87a" && signature != "GIF89a") return false
    var offset = 13
    val screenPacked = bytes[10].toInt() and 0xff
    if (screenPacked and 0x80 != 0) {
        offset += 3 * (1 shl ((screenPacked and 0x07) + 1))
    }
    if (offset > bytes.size) return false
    var frameCount = 0
    var firstFrameSignature: ByteArray? = null
    var hasDistinctFrame = false
    while (offset < bytes.size) {
        when (bytes[offset].toInt() and 0xff) {
            0x21 -> {
                if (offset + 2 > bytes.size) return false
                offset = skipGifSubBlocks(bytes, offset + 2) ?: return false
            }
            0x2c -> {
                if (offset + 10 > bytes.size) return false
                val frameStart = offset
                val imagePacked = bytes[offset + 9].toInt() and 0xff
                offset += 10
                if (imagePacked and 0x80 != 0) {
                    offset += 3 * (1 shl ((imagePacked and 0x07) + 1))
                }
                if (offset >= bytes.size) return false
                val frameEnd = skipGifSubBlocks(bytes, offset + 1) ?: return false
                val frameSignature = bytes.copyOfRange(frameStart, frameEnd)
                val firstSignature = firstFrameSignature
                if (firstSignature == null) {
                    firstFrameSignature = frameSignature
                } else if (!frameSignature.contentEquals(firstSignature)) {
                    hasDistinctFrame = true
                }
                frameCount += 1
                offset = frameEnd
            }
            0x3b -> break
            else -> return false
        }
    }
    return frameCount > 1 && hasDistinctFrame
}

private fun skipGifSubBlocks(bytes: ByteArray, start: Int): Int? {
    var offset = start
    while (offset < bytes.size) {
        val length = bytes[offset].toInt() and 0xff
        offset += 1
        if (length == 0) return offset
        if (offset + length > bytes.size) return null
        offset += length
    }
    return null
}

internal class ProgressiveAssetDelivery<T>(
    private val isAnimated: (T) -> Boolean = { false }
) {
    private var fallbackDelivered = false
    private var resolutionDelivered = false
    private var fallbackAsset: T? = null
    private var resolutionAsset: T? = null
    private var deliveredAsset: T? = null

    @Synchronized
    fun deliverFallback(asset: T, receiver: (T) -> Unit) {
        fallbackDelivered = true
        fallbackAsset = asset
        val current = deliveredAsset
        val canUpgradeResolution = resolutionDelivered && resolutionAsset?.let { !isAnimated(it) } == true && isAnimated(asset)
        if ((!resolutionDelivered && (current == null || !isAnimated(current))) || canUpgradeResolution) {
            deliveredAsset = asset
            receiver(asset)
        }
    }

    @Synchronized
    fun deliverResolution(asset: T?, receiver: (T?) -> Unit) {
        if (asset == null) {
            if (!fallbackDelivered) receiver(null)
            return
        }
        if (fallbackAsset?.let(isAnimated) == true && !isAnimated(asset)) return
        if (deliveredAsset?.let(isAnimated) == true && !isAnimated(asset)) return
        resolutionDelivered = true
        resolutionAsset = asset
        deliveredAsset = asset
        receiver(asset)
    }
}

class ShowdownSpriteCache(context: Context) : AutoCloseable {
    class SpriteAsset private constructor(
        private val bitmap: Bitmap?,
        private val movie: Movie?,
        private val width: Int,
        private val height: Int,
        private val mirrorWhenDrawn: Boolean = false
    ) {
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

        val isAnimated get() = movie != null

        fun trimHorizontalTransparentPadding(): SpriteAsset {
            val image = bitmap ?: return this
            var left = image.width
            var right = -1
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if ((image.getPixel(x, y) ushr 24) != 0) {
                        left = minOf(left, x)
                        right = maxOf(right, x)
                    }
                }
            }
            if (right < left) return this
            if (left == 0 && right == image.width - 1) return this
            return SpriteAsset(
                Bitmap.createBitmap(image, left, 0, right - left + 1, image.height),
                null,
                right - left + 1,
                image.height,
                mirrorWhenDrawn
            )
        }

        fun withMirrorWhenDrawn(mirror: Boolean) = SpriteAsset(bitmap, movie, width, height, mirror)

        fun draw(canvas: Canvas, destination: RectF, elapsedMillis: Long, flipHorizontally: Boolean = mirrorWhenDrawn, alpha: Int = 255) {
            val scale = min(destination.width() / width, destination.height() / height)
            val drawWidth = width * scale
            val drawHeight = height * scale
            val left = destination.centerX() - drawWidth / 2f
            val top = destination.centerY() - drawHeight / 2f
            canvas.save()
            if (flipHorizontally) canvas.scale(-1f, 1f, destination.centerX(), destination.centerY())
            if (alpha < 255) canvas.saveLayerAlpha(destination, alpha.coerceIn(0, 255))
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, Rect(0, 0, width, height), RectF(left, top, left + drawWidth, top + drawHeight), bitmapPaint)
                if (alpha < 255) canvas.restore()
                canvas.restore()
                return
            }
            movie ?: run {
                if (alpha < 255) canvas.restore()
                canvas.restore()
                return
            }
            movie.setTime((elapsedMillis % movie.duration().toLong().coerceAtLeast(1L)).toInt())
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            movie.draw(canvas, 0f, 0f)
            if (alpha < 255) canvas.restore()
            canvas.restore()
        }

        companion object {
            fun fromBitmap(bitmap: Bitmap, mirrorWhenDrawn: Boolean = false) = SpriteAsset(bitmap, null, bitmap.width, bitmap.height, mirrorWhenDrawn)

            fun fromMovie(movie: Movie, mirrorWhenDrawn: Boolean = false) = SpriteAsset(null, movie, movie.width(), movie.height(), mirrorWhenDrawn)
        }

    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadExecutor = Executors.newFixedThreadPool(4)
    private val memoryCache = LruCache<String, SpriteAsset>(16)
    private val pendingSpriteReceivers = ConcurrentHashMap<String, MutableList<(SpriteAsset?) -> Unit>>()
    private val pendingFileReceivers = ConcurrentHashMap<String, MutableList<(File?) -> Unit>>()
    private val diskCache = File(context.cacheDir, "showdown-resources").apply { mkdirs() }
    private val fallbackBackdrop = BitmapFactory.decodeResource(context.resources, R.drawable.battle_background_fallback)

    fun requestPokemon(request: BattleSpriteRequest, receiver: (SpriteAsset?) -> Unit) {
        if (request.style == BattleSession.SpriteStyle.MODERN_3D) {
            val delivery = ProgressiveAssetDelivery<SpriteAsset>(SpriteAsset::isAnimated)
            requestResolutionPlan(
                request = request,
                plan = ShowdownAssetPaths.battleSpriteResolutionPlan(request)
            ) { asset ->
                delivery.deliverResolution(asset, receiver)
            }
            requestPokeApiFallbackSprite(request) { asset ->
                asset?.let { delivery.deliverFallback(it, receiver) }
            }
            return
        }
        requestResolutionPlan(
            request = request,
            plan = ShowdownAssetPaths.battleSpriteResolutionPlan(request),
            receiver = receiver
        )
    }

    fun requestDexSprite(species: String, receiver: (SpriteAsset?) -> Unit) {
        val request = BattleSpriteRequest.forOpponent(species, BattleSession.SpriteStyle.MODERN_3D)
        requestResolutionPlan(
            request = request,
            plan = ShowdownAssetPaths.dexSpriteResolutionPlan(species),
            receiver = receiver
        )
    }

    fun requestTrainer(trainer: String, receiver: (SpriteAsset?) -> Unit) {
        requestSprite(ShowdownAssetPaths.trainer(trainer)) { receiver(it?.trimHorizontalTransparentPadding()) }
    }

    fun requestItem(item: String, receiver: (SpriteAsset?) -> Unit) {
        val paths = ShowdownAssetPaths.itemSpriteCandidates(item)
        if (paths.isEmpty()) {
            mainHandler.post { receiver(null) }
            return
        }
        requestSpriteCandidates(paths, receiver)
    }

    fun requestBackdrop(name: String = "bg-aquacordetown.jpg", receiver: (Bitmap?) -> Unit) {
        fallbackBackdrop?.let { fallback ->
            mainHandler.post { receiver(fallback) }
        }
        requestBytes("sprites/gen6bgs/$name") { file ->
            val bitmap = file?.let { BitmapFactory.decodeFile(it.path) }
            receiver(bitmap ?: fallbackBackdrop)
        }
    }

    fun requestEffect(name: String, receiver: (Bitmap?) -> Unit) {
        requestBytes("fx/$name") { file ->
            receiver(file?.let { BitmapFactory.decodeFile(it.path) })
        }
    }

    fun requestAudio(path: String, receiver: (File?) -> Unit) {
        requestBytes(path, receiver)
    }

    fun requestMoveDex(receiver: (File?) -> Unit) {
        requestBytes("data/moves.json", receiver)
    }

    fun requestPokedex(receiver: (File?) -> Unit) {
        requestBytes("data/pokedex.json", receiver)
    }

    fun requestItems(receiver: (File?) -> Unit) {
        requestBytes("data/items.js", receiver)
    }

    fun requestAbilities(receiver: (File?) -> Unit) {
        requestBytes("data/abilities.js", receiver)
    }

    fun requestLearnsets(receiver: (File?) -> Unit) {
        requestBytes("data/learnsets.js", receiver)
    }

    override fun close() {
        downloadExecutor.shutdownNow()
        memoryCache.evictAll()
        pendingSpriteReceivers.clear()
        pendingFileReceivers.clear()
    }

    private fun requestSprite(path: String, receiver: (SpriteAsset?) -> Unit) {
        memoryCache.get(path)?.let {
            mainHandler.post { receiver(it) }
            return
        }
        var shouldStart = false
        pendingSpriteReceivers.compute(path) { _, existing ->
            if (existing == null) {
                shouldStart = true
                mutableListOf(receiver)
            } else {
                existing.apply { add(receiver) }
            }
        } ?: return
        if (!shouldStart) return
        downloadExecutor.execute {
            val asset = loadBytes(path)?.let { decodeSprite(it, path) }
            if (asset != null) memoryCache.put(path, asset)
            val receivers = pendingSpriteReceivers.remove(path).orEmpty()
            mainHandler.post { receivers.forEach { it(asset) } }
        }
    }

    private fun requestSpriteCandidates(paths: List<String>, receiver: (SpriteAsset?) -> Unit) {
        requestSpriteCandidates(paths, animatedOnly = false, receiver)
    }

    private fun requestAnimatedSpriteCandidates(paths: List<String>, receiver: (SpriteAsset?) -> Unit) {
        requestSpriteCandidates(paths, animatedOnly = true, receiver)
    }

    private fun requestSpriteCandidates(
        paths: List<String>,
        animatedOnly: Boolean,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val usablePaths = paths.filterNot(::isGenericSpritePlaceholder)

        fun request(index: Int) {
            if (index >= usablePaths.size) {
                receiver(null)
                return
            }
            val path = usablePaths[index]
            requestSprite(path) { asset ->
                if (asset != null && (!requiresAnimatedSprite(path, animatedOnly) || asset.isAnimated)) receiver(asset) else request(index + 1)
            }
        }
        request(0)
    }

    private fun requestResolutionPlan(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        if (!plan.usesModernAnimatedFallback) {
            requestSpriteCandidates(plan.allCandidates, receiver)
            return
        }
        requestAnimatedSpriteCandidates(plan.preferredRemoteCandidates) { asset ->
            if (asset != null) {
                receiver(asset)
                return@requestAnimatedSpriteCandidates
            }
            if (request.backFacing) {
                requestBackSpriteResolution(request, plan, receiver)
            } else {
                requestFrontSpriteResolution(request, plan, receiver)
            }
        }
    }

    private fun requestBackSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestScrapedBackSpriteResolution(request, highResolutionOnly = true) { scrapedAsset ->
            if (scrapedAsset != null) {
                receiver(scrapedAsset)
            } else {
                requestAnimatedSpriteCandidates(plan.communityRemoteCandidates) { communityAsset ->
                    if (communityAsset != null) {
                        receiver(communityAsset)
                    } else {
                        requestRegularRemoteSpriteResolution(plan) { regularRemoteAsset ->
                            if (regularRemoteAsset != null) {
                                receiver(regularRemoteAsset)
                            } else {
                                requestScrapedBackSpriteResolution(request, highResolutionOnly = false) { scrapedRegularAsset ->
                                    if (scrapedRegularAsset != null) {
                                        receiver(scrapedRegularAsset)
                                    } else {
                                        requestModernLocalSpriteResolution(request, plan) { modernLocalAsset ->
                                            if (modernLocalAsset != null) {
                                                receiver(modernLocalAsset)
                                            } else {
                                                receiver(null)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestScrapedBackSpriteResolution(
        request: BattleSpriteRequest,
        highResolutionOnly: Boolean,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val indexUrls = if (highResolutionOnly) {
            ShowdownAssetPaths.highResolutionBackSpriteIndexUrls(request.shiny)
        } else {
            ShowdownAssetPaths.backSpriteIndexUrls(request.shiny)
        }
        val speciesNames = ShowdownAssetPaths.pokeApiLookupNames(request.species)

        fun requestIndex(index: Int) {
            if (index >= indexUrls.size) {
                receiver(null)
                return
            }
            val indexUrl = indexUrls[index]
            requestBytes(indexUrl) { file ->
                val html = file?.let { cachedFile -> runCatching { cachedFile.readText() }.getOrNull() }
                val pageUrls = if (html == null) emptyList() else {
                    listOf(indexUrl) + ShowdownSpriteIndexGroups.pageUrls(html, indexUrl)
                }.distinct()

                fun requestPage(pageIndex: Int, pageFile: File?) {
                    if (pageIndex >= pageUrls.size) {
                        requestIndex(index + 1)
                        return
                    }
                    val candidates = pageFile?.let { cachedFile ->
                        runCatching {
                            val pageHtml = cachedFile.readText()
                            if (highResolutionOnly) {
                                ShowdownBackSpriteIndex.highResolutionCandidates(pageHtml, speciesNames, request.shiny)
                            } else {
                                ShowdownBackSpriteIndex.candidates(pageHtml, speciesNames, request.shiny)
                            }
                        }.getOrDefault(emptyList())
                    }.orEmpty()
                    requestAnimatedSpriteCandidates(candidates) { asset ->
                        if (asset != null) {
                            receiver(asset)
                        } else {
                            val nextPageIndex = pageIndex + 1
                            if (nextPageIndex >= pageUrls.size) {
                                requestIndex(index + 1)
                            } else {
                                requestBytes(pageUrls[nextPageIndex]) { nextPageFile ->
                                    requestPage(nextPageIndex, nextPageFile)
                                }
                            }
                        }
                    }
                }

                requestPage(0, file)
            }
        }

        requestIndex(0)
    }

    private fun requestFrontSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestScrapedFrontSpriteResolution(request, highResolutionOnly = true) { scrapedAsset ->
            if (scrapedAsset != null) {
                receiver(scrapedAsset)
            } else {
                requestAnimatedSpriteCandidates(plan.communityRemoteCandidates) { communityAsset ->
                    if (communityAsset != null) {
                        receiver(communityAsset)
                    } else {
                        requestRegularRemoteSpriteResolution(plan) { regularRemoteAsset ->
                            if (regularRemoteAsset != null) {
                                receiver(regularRemoteAsset)
                            } else {
                                requestScrapedFrontSpriteResolution(request, highResolutionOnly = false) { regularScrapedAsset ->
                                    if (regularScrapedAsset != null) {
                                        receiver(regularScrapedAsset)
                                    } else {
                                        requestScavioAnimatedSprite(request) { scavioAsset ->
                                            if (scavioAsset != null) {
                                                receiver(scavioAsset)
                                            } else {
                                                requestModernLocalSpriteResolution(request, plan, receiver)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestScrapedFrontSpriteResolution(
        request: BattleSpriteRequest,
        highResolutionOnly: Boolean,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val indexUrls = if (highResolutionOnly) {
            ShowdownAssetPaths.highResolutionFrontSpriteIndexUrls(request.shiny)
        } else {
            ShowdownAssetPaths.frontSpriteIndexUrls(request.shiny)
        }
        val speciesNames = ShowdownAssetPaths.pokeApiLookupNames(request.species)

        fun requestIndex(index: Int) {
            if (index >= indexUrls.size) {
                receiver(null)
                return
            }
            val indexUrl = indexUrls[index]
            requestBytes(indexUrl) { file ->
                val html = file?.let { cachedFile -> runCatching { cachedFile.readText() }.getOrNull() }
                val pageUrls = if (html == null) emptyList() else {
                    listOf(indexUrl) + ShowdownSpriteIndexGroups.pageUrls(html, indexUrl)
                }.distinct()

                fun requestPage(pageIndex: Int, pageFile: File?) {
                    if (pageIndex >= pageUrls.size) {
                        requestIndex(index + 1)
                        return
                    }
                    val candidates = pageFile?.let { cachedFile ->
                        runCatching { ShowdownFrontSpriteIndex.candidates(cachedFile.readText(), speciesNames, request.shiny) }.getOrDefault(emptyList())
                    }.orEmpty().let { allCandidates ->
                        if (highResolutionOnly) {
                            allCandidates.filter { candidate ->
                                isHighResolutionSpritePath(candidate)
                            }
                        } else {
                            allCandidates
                        }
                    }
                    requestAnimatedSpriteCandidates(candidates) { asset ->
                        if (asset != null) {
                            receiver(asset)
                        } else {
                            val nextPageIndex = pageIndex + 1
                            if (nextPageIndex >= pageUrls.size) {
                                requestIndex(index + 1)
                            } else {
                                requestBytes(pageUrls[nextPageIndex]) { nextPageFile ->
                                    requestPage(nextPageIndex, nextPageFile)
                                }
                            }
                        }
                    }
                }

                requestPage(0, file)
            }
        }

        requestIndex(0)
    }

    private fun requestModernLocalSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val modernLocalCandidates = plan.fallbackCandidates.filter(::isModernLocalCandidate)
        requestAnimatedSpriteCandidates(modernLocalCandidates) { modernLocalAsset ->
            if (modernLocalAsset != null) {
                receiver(modernLocalAsset)
            } else {
                requestSmallSpriteResolution(request) { animatedAsset ->
                    if (animatedAsset != null) {
                        receiver(animatedAsset)
                    } else {
                        requestStaticShowdownFallback(request, receiver)
                    }
                }
            }
        }
    }

    private fun requestRegularRemoteSpriteResolution(
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestAnimatedSpriteCandidates(plan.regularRemoteCandidates, receiver)
    }

    private fun requestScavioAnimatedSprite(request: BattleSpriteRequest, receiver: (SpriteAsset?) -> Unit) {
        val names = ShowdownAssetPaths.spriteSpeciesNamesForExternalLookup(request.species)

        fun requestLookup(index: Int) {
            if (index >= names.size) {
                receiver(null)
                return
            }
            requestBytes(ScavioAnimatedSpriteIndex.apiUrl(names[index])) { file ->
                val candidates = file?.let { cachedFile ->
                    runCatching { ScavioAnimatedSpriteIndex.candidates(cachedFile.readText()) }.getOrDefault(emptyList())
                }.orEmpty()
                requestAnimatedSpriteCandidates(candidates) { asset ->
                    if (asset != null) {
                        receiver(asset)
                    } else {
                        requestLookup(index + 1)
                    }
                }
            }
        }

        requestLookup(0)
    }

    private fun requestSmallSpriteResolution(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        if (request.backFacing) {
            requestPokeApiAnimatedSprite(request) { animatedRemoteAsset ->
                receiver(animatedRemoteAsset)
            }
            return
        }
        requestPokeApiAnimatedSprite(request) { animatedRemoteAsset ->
            if (animatedRemoteAsset != null) {
                receiver(animatedRemoteAsset)
            } else {
                receiver(null)
            }
        }
    }

    private fun requestStaticShowdownFallback(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val standardCandidates = ShowdownAssetPaths.staticDexSpriteCandidates(request.species)
            .filter { it.startsWith("sprites/dex/") }
            .filterNot(::isHighResolutionSpritePath)
        requestSpriteCandidates(standardCandidates) { asset ->
            receiver(asset?.withMirrorWhenDrawn(request.backFacing))
        }
    }

    private fun isModernLocalCandidate(path: String) =
        path.startsWith("sprites/xyani")

    private fun requestPokeApiAnimatedSprite(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestPokeApiSpriteCandidates(request, { resourceNumber ->
            listOf(ShowdownAssetPaths.pokeApiAnimatedSprite(resourceNumber, request.side, request.shiny))
        }, receiver)
    }

    private fun requestPokeApiFallbackSprite(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestPokeApiAnimatedSprite(request, receiver)
    }

    private fun requestPokeApiSpriteCandidates(
        request: BattleSpriteRequest,
        candidates: (Int) -> List<String>,
        receiver: (SpriteAsset?) -> Unit
    ) {
        fun requestLookup(index: Int) {
            val names = ShowdownAssetPaths.pokeApiLookupNames(request.species)
            if (index >= names.size) {
                receiver(null)
                return
            }
            val lookupUrl = "https://pokeapi.co/api/v2/pokemon/${names[index]}"
            requestBytes(lookupUrl) { file ->
                val payload = file?.let { cachedFile ->
                    runCatching { JSONObject(cachedFile.readText()) }.getOrNull()
                }
                val resourceNumber = payload?.optInt("id", 0)?.takeIf { it > 0 }
                if (resourceNumber == null) {
                    requestLookup(index + 1)
                    return@requestBytes
                }
                requestAnimatedSpriteCandidates(candidates(resourceNumber)) { asset ->
                    if (asset != null) receiver(asset) else requestLookup(index + 1)
                }
            }
        }
        requestLookup(0)
    }

    private fun requestBytes(path: String, receiver: (File?) -> Unit) {
        var shouldStart = false
        pendingFileReceivers.compute(path) { _, existing ->
            if (existing == null) {
                shouldStart = true
                mutableListOf(receiver)
            } else {
                existing.apply { add(receiver) }
            }
        } ?: return
        if (!shouldStart) return
        runCatching {
            downloadExecutor.execute {
                val file = loadBytes(path)
                val receivers = pendingFileReceivers.remove(path).orEmpty()
                mainHandler.post { receivers.forEach { it(file) } }
            }
        }.onFailure {
            val receivers = pendingFileReceivers.remove(path).orEmpty()
            mainHandler.post { receivers.forEach { it(null) } }
        }
    }

    private fun loadBytes(path: String): File? {
        val extension = showdownCacheExtension(path)
        val file = File(diskCache, "${digest(path)}.$extension")
        return runCatching {
            if (!file.isFile) write(file, download(path) ?: return null)
            file.setLastModified(System.currentTimeMillis())
            trimDiskCache()
            file
        }.getOrNull()
    }

    private fun decodeSprite(file: File, path: String): SpriteAsset? {
        return if (path.endsWith(".gif", ignoreCase = true)) {
            if (!hasMultipleGifFrames(file.readBytes())) return null
            Movie.decodeFile(file.path)?.takeIf { it.width() > 0 && it.height() > 0 && it.duration() > 0 }?.let(SpriteAsset::fromMovie)
        } else {
            if (isHighResolutionSpritePath(path)) return null
            BitmapFactory.decodeFile(file.path)?.let(SpriteAsset::fromBitmap)
        }
    }

    private fun download(path: String): ByteArray? {
        val url = if (path.startsWith("https://")) path else "https://play.pokemonshowdown.com/$path"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Showdown-Android/0.1")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.contentLength > MAX_FILE_BYTES) return null
            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_FILE_BYTES) return null
                        output.write(buffer, 0, count)
                    }
                    output.toByteArray()
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun write(file: File, bytes: ByteArray) {
        val temporary = File(file.parentFile, "${file.name}.part")
        FileOutputStream(temporary).use { it.write(bytes) }
        if (!temporary.renameTo(file)) throw IOException("Unable to cache Showdown resource")
    }

    private fun trimDiskCache() {
        val files = diskCache.listFiles()?.sortedBy { it.lastModified() } ?: return
        var total = files.sumOf { it.length() }
        files.forEach {
            val length = it.length()
            if (total > MAX_DISK_BYTES && it.delete()) total -= length
        }
    }

    private fun digest(value: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(Locale.ROOT, it) }
    }

    private companion object {
        const val MAX_FILE_BYTES = 64 * 1024 * 1024
        const val MAX_DISK_BYTES = 256L * 1024L * 1024L
    }
}
