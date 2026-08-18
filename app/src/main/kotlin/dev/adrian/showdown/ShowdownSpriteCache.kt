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

internal class ProgressiveAssetDelivery<T> {
    private var fallbackDelivered = false
    private var resolutionDelivered = false

    @Synchronized
    fun deliverFallback(asset: T, receiver: (T) -> Unit) {
        if (resolutionDelivered) return
        fallbackDelivered = true
        receiver(asset)
    }

    @Synchronized
    fun deliverResolution(asset: T?, receiver: (T?) -> Unit) {
        if (asset == null) {
            if (!fallbackDelivered) receiver(null)
            return
        }
        resolutionDelivered = true
        receiver(asset)
    }
}

class ShowdownSpriteCache(context: Context) : AutoCloseable {
    class SpriteAsset private constructor(
        private val bitmap: Bitmap?,
        private val movie: Movie?,
        private val width: Int,
        private val height: Int
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
            return fromBitmap(Bitmap.createBitmap(image, left, 0, right - left + 1, image.height))
        }

        fun draw(canvas: Canvas, destination: RectF, elapsedMillis: Long, flipHorizontally: Boolean = false, alpha: Int = 255) {
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
            movie.setTime((elapsedMillis % maxOf(movie.duration(), 1000)).toInt())
            canvas.translate(left, top)
            canvas.scale(scale, scale)
            movie.draw(canvas, 0f, 0f)
            if (alpha < 255) canvas.restore()
            canvas.restore()
        }

        companion object {
            fun fromBitmap(bitmap: Bitmap) = SpriteAsset(bitmap, null, bitmap.width, bitmap.height)

            fun fromMovie(movie: Movie) = SpriteAsset(null, movie, movie.width(), movie.height())
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
            val delivery = ProgressiveAssetDelivery<SpriteAsset>()
            requestPokeApiAnimatedSprite(request) { asset ->
                asset?.let { delivery.deliverFallback(it, receiver) }
            }
            requestResolutionPlan(
                request = request,
                plan = ShowdownAssetPaths.battleSpriteResolutionPlan(request)
            ) { asset ->
                delivery.deliverResolution(asset, receiver)
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
        fun request(index: Int) {
            if (index >= paths.size) {
                receiver(null)
                return
            }
            requestSprite(paths[index]) { asset ->
                if (asset != null) receiver(asset) else request(index + 1)
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
        requestSpriteCandidates(plan.preferredRemoteCandidates) { asset ->
            if (asset != null) {
                receiver(asset)
                return@requestSpriteCandidates
            }
            requestPokeApiModernHdSprite(request) { modernHdAsset ->
                if (modernHdAsset != null) {
                    receiver(modernHdAsset)
                    return@requestPokeApiModernHdSprite
                }
                if (request.backFacing) {
                    requestBackSpriteResolution(request, plan, receiver)
                } else {
                    requestFrontSpriteResolution(request, plan, receiver)
                }
            }
        }
    }

    private fun requestBackSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestScrapedBackSpriteResolution(request) { scrapedAsset ->
            if (scrapedAsset != null) {
                receiver(scrapedAsset)
            } else {
                requestRegularRemoteSpriteResolution(plan) { regularRemoteAsset ->
                    if (regularRemoteAsset != null) {
                        receiver(regularRemoteAsset)
                    } else {
                        requestVerifiedBackThenCommunitySpriteResolution(request, plan, receiver)
                    }
                }
            }
        }
    }

    private fun requestScrapedBackSpriteResolution(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val indexUrls = ShowdownAssetPaths.backSpriteIndexUrls(request.shiny)
        val speciesNames = ShowdownAssetPaths.pokeApiLookupNames(request.species)

        fun requestIndex(index: Int) {
            if (index >= indexUrls.size) {
                receiver(null)
                return
            }
            requestBytes(indexUrls[index]) { file ->
                val candidates = file?.let { cachedFile ->
                    runCatching { ShowdownBackSpriteIndex.candidates(cachedFile.readText(), speciesNames, request.shiny) }.getOrDefault(emptyList())
                }.orEmpty()
                requestSpriteCandidates(candidates) { asset ->
                    if (asset != null) receiver(asset) else requestIndex(index + 1)
                }
            }
        }

        requestIndex(0)
    }

    private fun requestVerifiedBackThenCommunitySpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestSpriteCandidates(plan.verifiedRemoteCandidates) { verifiedAsset ->
            if (verifiedAsset != null) {
                receiver(verifiedAsset)
            } else {
                requestCommunityThenModernLocalSpriteResolution(request, plan, receiver)
            }
        }
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
                requestPokeApiHighResolutionSprite(request) { highResolutionAsset ->
                    if (highResolutionAsset != null) {
                        receiver(highResolutionAsset)
                    } else {
                        requestSpriteCandidates(plan.communityRemoteCandidates) { communityAsset ->
                            if (communityAsset != null) {
                                receiver(communityAsset)
                            } else {
                                requestScrapedFrontSpriteResolution(request, highResolutionOnly = false) { regularScrapedAsset ->
                                    if (regularScrapedAsset != null) {
                                        receiver(regularScrapedAsset)
                                    } else {
                                        requestRegularRemoteSpriteResolution(plan) { regularRemoteAsset ->
                                            if (regularRemoteAsset != null) {
                                                receiver(regularRemoteAsset)
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
        val indexUrls = ShowdownAssetPaths.frontSpriteIndexUrls(request.shiny)
        val speciesNames = ShowdownAssetPaths.pokeApiLookupNames(request.species)

        fun requestIndex(index: Int) {
            if (index >= indexUrls.size) {
                receiver(null)
                return
            }
            requestBytes(indexUrls[index]) { file ->
                val candidates = file?.let { cachedFile ->
                    runCatching { ShowdownFrontSpriteIndex.candidates(cachedFile.readText(), speciesNames, request.shiny) }.getOrDefault(emptyList())
                }.orEmpty().let { allCandidates ->
                    if (highResolutionOnly) {
                        allCandidates.filter { candidate ->
                            candidate.contains("/animados-gigante/") || candidate.contains("/animados-sinbordes-gigante/")
                        }
                    } else {
                        allCandidates
                    }
                }
                requestSpriteCandidates(candidates) { asset ->
                    if (asset != null) receiver(asset) else requestIndex(index + 1)
                }
            }
        }

        requestIndex(0)
    }

    private fun requestCommunityThenModernLocalSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestSpriteCandidates(plan.communityRemoteCandidates) { communityAsset ->
            if (communityAsset != null) {
                receiver(communityAsset)
            } else {
                requestModernLocalSpriteResolution(request, plan, receiver)
            }
        }
    }

    private fun requestModernLocalSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val modernLocalCandidates = plan.fallbackCandidates.filter(::isModernLocalCandidate)
        requestSpriteCandidates(modernLocalCandidates) { modernLocalAsset ->
            if (modernLocalAsset != null) {
                receiver(modernLocalAsset)
            } else {
                requestSmallSpriteResolution(request, plan, receiver)
            }
        }
    }

    private fun requestRegularRemoteSpriteResolution(
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestSpriteCandidates(plan.regularRemoteCandidates, receiver)
    }

    private fun requestSmallSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        if (request.backFacing) {
            requestPokeApiAnimatedSprite(request) { animatedRemoteAsset ->
                if (animatedRemoteAsset != null) receiver(animatedRemoteAsset) else requestPokeApiStandardSprite(request, receiver)
            }
            return
        }
        requestPokeApiAnimatedSprite(request) { animatedRemoteAsset ->
            if (animatedRemoteAsset != null) {
                receiver(animatedRemoteAsset)
            } else {
                requestSpriteCandidates(plan.verifiedRemoteCandidates) { verifiedAsset ->
                    if (verifiedAsset != null) {
                        receiver(verifiedAsset)
                    } else if (request.side == BattleSpriteSide.OPPONENT) {
                        requestPokeApiHighResolutionSprite(request) { highResolutionAsset ->
                            if (highResolutionAsset != null) {
                                receiver(highResolutionAsset)
                            } else {
                                requestPokeApiStandardSprite(request, receiver)
                            }
                        }
                    } else {
                        requestPokeApiStandardSprite(request, receiver)
                    }
                }
            }
        }
    }

    private fun isModernLocalCandidate(path: String) =
        path.startsWith("sprites/xyani") || path.startsWith("sprites/xy/")

    private fun requestPokeApiModernHdSprite(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestPokeApiSpriteCandidates(request, { number ->
            ShowdownAssetPaths.hdAnimatedSpriteCandidates(number, request.side, request.shiny)
        }, receiver)
    }

    private fun requestPokeApiAnimatedSprite(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestPokeApiSpriteCandidates(request, { number ->
            listOf(ShowdownAssetPaths.pokeApiAnimatedSprite(number, request.side, request.shiny))
        }, receiver)
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
                val nationalDexNumber = file?.let { cachedFile ->
                    ShowdownAssetPaths.pokeApiNationalDexNumber(cachedFile.readText())
                } ?: resourceNumber
                if (resourceNumber == null || nationalDexNumber == null) {
                    requestLookup(index + 1)
                    return@requestBytes
                }
                requestSpriteCandidates(candidates(nationalDexNumber)) { asset ->
                    if (asset != null) receiver(asset) else requestLookup(index + 1)
                }
            }
        }
        requestLookup(0)
    }

    private fun requestPokeApiHighResolutionSprite(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        fun requestLookup(index: Int) {
            val names = ShowdownAssetPaths.pokeApiLookupNames(request.species)
            if (index >= names.size) {
                receiver(null)
                return
            }
            requestBytes("https://pokeapi.co/api/v2/pokemon/${names[index]}") { file ->
                val resourceNumber = file?.let { cachedFile ->
                    runCatching { JSONObject(cachedFile.readText()).optInt("id", 0) }.getOrNull()
                }?.takeIf { it > 0 }
                if (resourceNumber == null) {
                    requestLookup(index + 1)
                } else {
                        requestSprite(ShowdownAssetPaths.pokeApiHighResolutionSprite(resourceNumber, request.shiny)) { highResolutionAsset ->
                        if (highResolutionAsset != null) receiver(highResolutionAsset) else requestLookup(index + 1)
                    }
                }
            }
        }
        requestLookup(0)
    }

    private fun requestPokeApiStandardSprite(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        fun requestLookup(index: Int) {
            val names = ShowdownAssetPaths.pokeApiLookupNames(request.species)
            if (index >= names.size) {
                receiver(null)
                return
            }
            requestBytes("https://pokeapi.co/api/v2/pokemon/${names[index]}") { file ->
                val resourceNumber = file?.let { cachedFile ->
                    runCatching { JSONObject(cachedFile.readText()).optInt("id", 0) }.getOrNull()
                }?.takeIf { it > 0 }
                if (resourceNumber == null) {
                    requestLookup(index + 1)
                } else {
                    requestSprite(ShowdownAssetPaths.pokeApiStandardSprite(resourceNumber, request.side, request.shiny)) { standardAsset ->
                        if (standardAsset != null) receiver(standardAsset) else requestLookup(index + 1)
                    }
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
        return if (path.endsWith(".gif")) {
            Movie.decodeFile(file.path)?.takeIf { it.width() > 0 && it.height() > 0 }?.let(SpriteAsset::fromMovie)
        } else {
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
