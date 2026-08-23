package dev.adrian.showdown

import android.app.ActivityManager
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
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.lang.ref.WeakReference
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min
import kotlin.math.roundToInt
import org.json.JSONObject

internal fun isGenericSpritePlaceholder(path: String): Boolean {
    val fileName = path.substringBefore('?').substringAfterLast('/').substringBeforeLast('.')
    return fileName.equals("substitute", ignoreCase = true) || fileName.equals("decoy", ignoreCase = true)
}

internal fun isHighResolutionSpritePath(path: String): Boolean =
    path.contains("/sprites/animados", ignoreCase = true)

internal fun isAnimatedSpritePath(path: String): Boolean =
    path.contains("/sprites/animados", ignoreCase = true) ||
        path.startsWith("sprites/ani", ignoreCase = true) ||
        path.startsWith("sprites/xyani", ignoreCase = true) ||
        path.contains("/Animated_sprites_by_Ghasty001/", ignoreCase = true)

internal fun requiresAnimatedSprite(path: String, animatedOnly: Boolean): Boolean =
    animatedOnly || isAnimatedSpritePath(path)

internal fun allowsStaticShowdownFallback(request: BattleSpriteRequest): Boolean = !request.backFacing

private const val MAX_ANIMATED_FRAME_DIMENSION = 512
private const val MAX_ANIMATED_SOURCE_DIMENSION = 576
private const val MAX_ANIMATED_SOURCE_PIXELS = 576L * 576L
private const val MAX_ANIMATED_FILE_BYTES = 6L * 1024L * 1024L
private const val MAX_ANIMATED_FRAME_COUNT = 24
private const val MAX_STREAMED_HD_SOURCE_DIMENSION = 1024
private const val MAX_STREAMED_HD_SOURCE_PIXELS = 1024L * 1024L
private const val MAX_STREAMED_HD_FILE_BYTES = 8L * 1024L * 1024L
private const val MAX_STREAMED_HD_FRAME_COUNT = 96
private const val MOVIE_MEMORY_MULTIPLIER = 4L

internal fun boundedAnimatedFrameSize(sourceWidth: Int, sourceHeight: Int, maxDimension: Int): Pair<Int, Int> {
    if (sourceWidth <= 0 || sourceHeight <= 0) return 1 to 1
    val dimension = maxDimension.coerceAtLeast(1)
    val scale = min(1f, dimension.toFloat() / maxOf(sourceWidth, sourceHeight).toFloat())
    return (
        (sourceWidth * scale).roundToInt().coerceAtLeast(1) to
            (sourceHeight * scale).roundToInt().coerceAtLeast(1)
        )
}

internal fun gifCanvasSize(bytes: ByteArray): Pair<Int, Int>? {
    if (bytes.size < 13) return null
    val signature = bytes.copyOfRange(0, 6).toString(Charsets.US_ASCII)
    if (signature != "GIF87a" && signature != "GIF89a") return null
    val width = (bytes[6].toInt() and 0xff) or ((bytes[7].toInt() and 0xff) shl 8)
    val height = (bytes[8].toInt() and 0xff) or ((bytes[9].toInt() and 0xff) shl 8)
    return width to height
}

internal fun animatedGifFitsDecodeBudget(
    sourceWidth: Int,
    sourceHeight: Int,
    maxDimension: Int,
    maxPixels: Long
): Boolean = sourceWidth > 0 &&
    sourceHeight > 0 &&
    maxOf(sourceWidth, sourceHeight) <= maxDimension &&
    sourceWidth.toLong() * sourceHeight.toLong() <= maxPixels

internal fun hasMultipleGifFrames(bytes: ByteArray): Boolean =
    ByteArrayInputStream(bytes).use(::hasMultipleGifFrames)

internal fun hasMultipleGifFrames(file: File): Boolean =
    runCatching {
        BufferedInputStream(FileInputStream(file)).use { input -> hasMultipleGifFrames(input) }
    }.getOrDefault(false)

internal fun hasAnimatedGifFrameBudget(file: File, maxFrames: Int): Boolean =
    runCatching {
        BufferedInputStream(FileInputStream(file)).use { input -> hasMultipleGifFrames(input, maxFrames) }
    }.getOrDefault(false)

private fun hasMultipleGifFrames(input: InputStream, maxFrames: Int? = null): Boolean {
    val header = ByteArray(6)
    if (!readGifBytes(input, header)) return false
    val signature = header.toString(Charsets.US_ASCII)
    if (signature != "GIF87a" && signature != "GIF89a") return false
    val screen = ByteArray(7)
    if (!readGifBytes(input, screen)) return false
    val screenPacked = screen[4].toInt() and 0xff
    if (screenPacked and 0x80 != 0 && !skipGifBytes(input, 3 * (1 shl ((screenPacked and 0x07) + 1)))) return false
    var frameCount = 0
    var firstFrameSignature: ByteArray? = null
    var hasDistinctFrame = false
    while (true) {
        when (val marker = input.read()) {
            0x21 -> {
                if (input.read() < 0 || !consumeGifSubBlocks(input)) return false
            }
            0x2c -> {
                val frameDigest = MessageDigest.getInstance("SHA-256")
                frameDigest.update(marker.toByte())
                val descriptor = ByteArray(9)
                if (!readGifBytes(input, descriptor)) return false
                frameDigest.update(descriptor)
                val imagePacked = descriptor[8].toInt() and 0xff
                if (imagePacked and 0x80 != 0 && !readAndDigestGifBytes(
                        input,
                        3 * (1 shl ((imagePacked and 0x07) + 1)),
                        frameDigest
                    )
                ) return false
                val lzwMinimumCodeSize = input.read()
                if (lzwMinimumCodeSize < 0) return false
                frameDigest.update(lzwMinimumCodeSize.toByte())
                if (!consumeGifSubBlocks(input, frameDigest)) return false
                val frameSignature = frameDigest.digest()
                val firstSignature = firstFrameSignature
                if (firstSignature == null) {
                    firstFrameSignature = frameSignature
                } else if (!frameSignature.contentEquals(firstSignature)) {
                    hasDistinctFrame = true
                }
                frameCount += 1
                if (maxFrames != null && frameCount > maxFrames) return false
            }
            0x3b -> return frameCount > 1 && hasDistinctFrame
            -1 -> return false
            else -> return false
        }
    }
}

private fun readGifBytes(input: InputStream, bytes: ByteArray): Boolean {
    var offset = 0
    while (offset < bytes.size) {
        val count = input.read(bytes, offset, bytes.size - offset)
        if (count <= 0) return false
        offset += count
    }
    return true
}

private fun skipGifBytes(input: InputStream, byteCount: Int): Boolean {
    var remaining = byteCount
    val buffer = ByteArray(4096)
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count <= 0) return false
        remaining -= count
    }
    return true
}

private fun readAndDigestGifBytes(input: InputStream, byteCount: Int, digest: MessageDigest): Boolean {
    var remaining = byteCount
    val buffer = ByteArray(4096)
    while (remaining > 0) {
        val count = input.read(buffer, 0, minOf(buffer.size, remaining))
        if (count <= 0) return false
        digest.update(buffer, 0, count)
        remaining -= count
    }
    return true
}

private fun consumeGifSubBlocks(input: InputStream, digest: MessageDigest? = null): Boolean {
    while (true) {
        val length = input.read()
        if (length < 0) return false
        digest?.update(length.toByte())
        if (length == 0) return true
        if (digest == null) {
            if (!skipGifBytes(input, length)) return false
        } else if (!readAndDigestGifBytes(input, length, digest)) {
            return false
        }
    }
}

class ShowdownSpriteCache(context: Context) : AutoCloseable {
    class SpriteAsset private constructor(
        private val bitmap: Bitmap?,
        private val movie: Movie?,
        private val streamingGif: ShowdownStreamingGif?,
        private val width: Int,
        private val height: Int,
        private val sourceWidth: Int = width,
        private val sourceHeight: Int = height
    ) {
        private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        private var animatedFrame: Bitmap? = null
        private var animatedFrameTime = Long.MIN_VALUE

        val isAnimated get() = movie != null || streamingGif?.isAnimated == true

        fun stopAnimation() = Unit

        fun estimatedMemoryBytes(): Int {
            val pixels = width.toLong() * height.toLong() * 4L
            val streamedMemory = streamingGif?.estimatedMemoryBytes
            if (streamedMemory != null) return streamedMemory
            val sourcePixels = if (movie != null) sourceWidth.toLong() * sourceHeight.toLong() * 4L else 0L
            return pixels
                .plus(sourcePixels * MOVIE_MEMORY_MULTIPLIER)
                .coerceIn(1L, Int.MAX_VALUE.toLong())
                .toInt()
        }

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
                null,
                right - left + 1,
                image.height
            )
        }

        fun draw(
            canvas: Canvas,
            destination: RectF,
            elapsedMillis: Long,
            flipHorizontally: Boolean = false,
            alpha: Int = 255,
            animate: Boolean = true
        ) {
            val scale = min(destination.width() / width, destination.height() / height)
            val drawWidth = width * scale
            val drawHeight = height * scale
            val left = destination.centerX() - drawWidth / 2f
            val top = destination.centerY() - drawHeight / 2f
            canvas.save()
            if (flipHorizontally) canvas.scale(-1f, 1f, destination.centerX(), destination.centerY())
            if (alpha < 255) canvas.saveLayerAlpha(destination, alpha.coerceIn(0, 255))
            val image = bitmap ?: streamingGif?.frameAt(if (animate) elapsedMillis else 0L) ?: animatedFrameAt(elapsedMillis)
            if (image == null) {
                if (alpha < 255) canvas.restore()
                canvas.restore()
                return
            }
            canvas.drawBitmap(image, Rect(0, 0, width, height), RectF(left, top, left + drawWidth, top + drawHeight), bitmapPaint)
            if (alpha < 255) canvas.restore()
            canvas.restore()
        }

        private fun animatedFrameAt(elapsedMillis: Long): Bitmap? {
            val source = movie ?: return null
            val duration = source.duration().toLong().coerceAtLeast(1L)
            val frameTime = ((elapsedMillis / ANIMATED_FRAME_INTERVAL_MILLIS) * ANIMATED_FRAME_INTERVAL_MILLIS) % duration
            if (animatedFrame != null && animatedFrameTime == frameTime) return animatedFrame
            val frame = animatedFrame ?: Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            frame.eraseColor(0)
            source.setTime(frameTime.toInt())
            Canvas(frame).apply {
                scale(width / source.width().coerceAtLeast(1).toFloat(), height / source.height().coerceAtLeast(1).toFloat())
                source.draw(this, 0f, 0f)
            }
            animatedFrame = frame
            animatedFrameTime = frameTime
            return frame
        }

        companion object {
            private const val ANIMATED_FRAME_INTERVAL_MILLIS = 48L

            fun fromBitmap(bitmap: Bitmap) = SpriteAsset(bitmap, null, null, bitmap.width, bitmap.height)

            fun fromMovie(movie: Movie): SpriteAsset {
                val (frameWidth, frameHeight) = boundedAnimatedFrameSize(
                    movie.width(),
                    movie.height(),
                    MAX_ANIMATED_FRAME_DIMENSION
                )
                return SpriteAsset(null, movie, null, frameWidth, frameHeight, movie.width(), movie.height())
            }

            internal fun fromStreamingGif(gif: ShowdownStreamingGif): SpriteAsset? {
                if (!gif.isAnimated) {
                    gif.release()
                    return null
                }
                return SpriteAsset(
                    null,
                    null,
                    gif,
                    gif.frameWidth,
                    gif.frameHeight,
                    gif.sourceWidth,
                    gif.sourceHeight
                )
            }
        }

    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadExecutor = Executors.newFixedThreadPool(2)
    private val decodeExecutor = Executors.newSingleThreadExecutor()
    private val memoryConstrained = (context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager)
        ?.let { manager -> ActivityManager.MemoryInfo().also(manager::getMemoryInfo).totalMem < 2L * 1024L * 1024L * 1024L }
        ?: false
    private val memoryCache = object : LruCache<String, SpriteAsset>(SPRITE_MEMORY_CACHE_BYTES) {
        override fun sizeOf(key: String, value: SpriteAsset) = value.estimatedMemoryBytes()

        override fun entryRemoved(evicted: Boolean, key: String, oldValue: SpriteAsset, newValue: SpriteAsset?) {
            if (oldValue !== newValue) oldValue.stopAnimation()
        }
    }
    private val resolvedPokemonCache = LruCache<BattleSpriteRequest, WeakReference<SpriteAsset>>(16)
    private val pendingSpriteReceivers = ConcurrentHashMap<String, MutableList<(SpriteAsset?) -> Unit>>()
    private val pendingFileReceivers = ConcurrentHashMap<String, MutableList<(File?) -> Unit>>()
    private val diskCache = File(context.cacheDir, "showdown-resources").apply { mkdirs() }
    private val fallbackBackdrop = BitmapFactory.decodeResource(context.resources, R.drawable.battle_background_fallback)

    fun requestPokemon(request: BattleSpriteRequest, receiver: (SpriteAsset?) -> Unit) {
        resolvedPokemonCache.get(request)?.get()?.let { asset ->
            mainHandler.post { receiver(asset) }
            return
        } ?: resolvedPokemonCache.remove(request)
        requestResolutionPlan(
            request = request,
            plan = ShowdownAssetPaths.battleSpriteResolutionPlan(request),
            receiver = { asset ->
                if (asset != null && (asset.isAnimated || !request.backFacing)) {
                    resolvedPokemonCache.put(request, WeakReference(asset))
                }
                receiver(asset)
            }
        )
    }

    fun requestDexSprite(species: String, receiver: (SpriteAsset?) -> Unit) {
        requestDexSprite(species, shiny = false, receiver = receiver)
    }

    fun requestDexSprite(species: String, shiny: Boolean, receiver: (SpriteAsset?) -> Unit) {
        val request = BattleSpriteRequest.forOpponent(species, BattleSession.SpriteStyle.MODERN_3D, shiny)
        requestResolutionPlan(
            request = request,
            plan = ShowdownAssetPaths.dexSpriteResolutionPlan(species, shiny),
            receiver = receiver
        )
    }

    fun requestStaticDexSprite(species: String, receiver: (SpriteAsset?) -> Unit) {
        requestStaticDexSprite(species, shiny = false, receiver = receiver)
    }

    fun requestStaticDexSprite(species: String, shiny: Boolean, receiver: (SpriteAsset?) -> Unit) {
        requestPokeApiStaticSprite(species, shiny) { asset ->
            if (asset != null) {
                receiver(asset)
            } else {
                requestSpriteCandidates(ShowdownAssetPaths.staticDexSpriteCandidates(species, shiny), receiver)
            }
        }
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
        decodeExecutor.shutdownNow()
        memoryCache.evictAll()
        resolvedPokemonCache.evictAll()
        pendingSpriteReceivers.clear()
        pendingFileReceivers.clear()
    }

    fun clearMemory() {
        memoryCache.evictAll()
        resolvedPokemonCache.evictAll()
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
            val file = loadBytes(path)
            if (file == null) {
                finishSpriteRequest(path, null)
                return@execute
            }
            runCatching {
                decodeExecutor.execute {
                    val asset = runCatching { decodeSprite(file, path) }.getOrNull()
                    finishSpriteRequest(path, asset)
                }
            }.onFailure {
                finishSpriteRequest(path, null)
            }
        }
    }

    private fun finishSpriteRequest(path: String, asset: SpriteAsset?) {
        if (asset != null) memoryCache.put(path, asset)
        val receivers = pendingSpriteReceivers.remove(path).orEmpty()
        mainHandler.post { receivers.forEach { it(asset) } }
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
        if (memoryConstrained && plan.usesModernAnimatedFallback) {
            requestConstrainedSpriteResolution(request, plan, receiver)
            return
        }
        if (!plan.usesModernAnimatedFallback) {
            requestSpriteCandidates(plan.allCandidates, receiver)
            return
        }
        if (request.backFacing) {
            requestBackSpriteResolution(request, plan, receiver)
            return
        }
        requestAnimatedSpriteCandidates(plan.preferredRemoteCandidates) { asset ->
            if (asset != null) {
                receiver(asset)
                return@requestAnimatedSpriteCandidates
            }
            requestFrontSpriteResolution(request, plan, receiver)
        }
    }

    private fun requestConstrainedSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestAnimatedSpriteCandidates(plan.preferredRemoteCandidates) { hdAsset ->
            if (hdAsset != null) {
                receiver(hdAsset)
            } else {
                requestAnimatedSpriteCandidates(plan.regularRemoteCandidates) { regularAsset ->
                    if (regularAsset != null) {
                        receiver(regularAsset)
                    } else {
                        requestAnimatedSpriteCandidates(plan.communityRemoteCandidates) { communityAsset ->
                            if (communityAsset != null) {
                                receiver(communityAsset)
                            } else {
                                requestModernLocalSpriteResolution(request, plan) { asset ->
                                    if (asset != null || !request.backFacing) {
                                        receiver(asset)
                                    } else {
                                        requestStaticShowdownBackFallback(request, receiver)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun requestBackSpriteResolution(
        request: BattleSpriteRequest,
        plan: ShowdownSpriteResolutionPlan,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val animatedTiers: List<((SpriteAsset?) -> Unit) -> Unit> = listOf(
            { callback -> requestAnimatedSpriteCandidates(plan.preferredRemoteCandidates, callback) },
            { callback -> requestScrapedBackSpriteResolution(request, highResolutionOnly = true, receiver = callback) },
            { callback -> requestRegularRemoteSpriteResolution(plan, callback) },
            { callback -> requestScrapedBackSpriteResolution(request, highResolutionOnly = false, receiver = callback) },
            { callback -> requestAnimatedSpriteCandidates(plan.communityRemoteCandidates, callback) },
            { callback -> requestModernLocalSpriteResolution(request, plan, callback) }
        )

        fun requestTier(index: Int) {
            if (index >= animatedTiers.size) {
                requestStaticShowdownBackFallback(request, receiver)
                return
            }
            animatedTiers[index] { asset ->
                if (asset != null) {
                    receiver(asset)
                } else {
                    requestTier(index + 1)
                }
            }
        }

        requestTier(0)
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
                requestScavioAnimatedSprite(request) { scavioAsset ->
                    if (scavioAsset != null) {
                        receiver(scavioAsset)
                    } else {
                        requestRegularRemoteSpriteResolution(plan) { regularRemoteAsset ->
                            if (regularRemoteAsset != null) {
                                receiver(regularRemoteAsset)
                            } else {
                                requestScrapedFrontSpriteResolution(request, highResolutionOnly = false) { regularScrapedAsset ->
                                    if (regularScrapedAsset != null) {
                                        receiver(regularScrapedAsset)
                                    } else {
                                        requestAnimatedSpriteCandidates(plan.communityRemoteCandidates) { communityAsset ->
                                            if (communityAsset != null) {
                                                receiver(communityAsset)
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
                    } else if (allowsStaticShowdownFallback(request)) {
                        requestPokeApiStaticSprite(request.species, request.shiny) { staticAsset ->
                            if (staticAsset != null) receiver(staticAsset) else requestStaticShowdownFallback(request, receiver)
                        }
                    } else {
                        receiver(null)
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
        if (request.shiny) {
            receiver(null)
            return
        }
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

    private fun requestStaticShowdownBackFallback(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestSpriteCandidates(ShowdownAssetPaths.staticBackSpriteCandidates(request.species), receiver)
    }

    private fun requestStaticShowdownFallback(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        val standardCandidates = ShowdownAssetPaths.staticDexSpriteCandidates(request.species, request.shiny)
            .filter { it.startsWith("sprites/dex/") || it.startsWith("sprites/dex-shiny/") }
            .filterNot(::isHighResolutionSpritePath)
        requestSpriteCandidates(standardCandidates) { asset ->
            receiver(asset)
        }
    }

    private fun isModernLocalCandidate(path: String) =
        path.startsWith("sprites/xyani")

    private fun requestPokeApiAnimatedSprite(
        request: BattleSpriteRequest,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestPokeApiSpriteCandidates(request.species, animatedOnly = true, { resourceNumber ->
            listOf(ShowdownAssetPaths.pokeApiAnimatedSprite(resourceNumber, request.side, request.shiny))
        }, receiver)
    }

    private fun requestPokeApiStaticSprite(
        species: String,
        shiny: Boolean,
        receiver: (SpriteAsset?) -> Unit
    ) {
        requestPokeApiSpriteCandidates(species, animatedOnly = false, { resourceNumber ->
            if (shiny) {
                listOf("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/shiny/$resourceNumber.png")
            } else {
                listOf("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/$resourceNumber.png")
            }
        }, receiver)
    }

    private fun requestPokeApiSpriteCandidates(
        species: String,
        animatedOnly: Boolean,
        candidates: (Int) -> List<String>,
        receiver: (SpriteAsset?) -> Unit
    ) {
        fun requestLookup(index: Int) {
            val names = ShowdownAssetPaths.pokeApiLookupNames(species)
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
                requestSpriteCandidates(candidates(resourceNumber), animatedOnly) { asset ->
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
            if (!file.isFile && !downloadTo(file, path)) return null
            file.setLastModified(System.currentTimeMillis())
            trimDiskCache()
            file
        }.getOrNull()
    }

    private fun decodeSprite(file: File, path: String): SpriteAsset? {
        if (isAnimatedSpritePath(path) && !path.endsWith(".gif", ignoreCase = true)) return null
        return if (path.endsWith(".gif", ignoreCase = true)) {
            val canvasSize = readGifCanvasSize(file) ?: return null
            val fileBytes = file.length()
            val fitsMovieBudget = animatedGifFitsDecodeBudget(
                sourceWidth = canvasSize.first,
                sourceHeight = canvasSize.second,
                maxDimension = MAX_ANIMATED_SOURCE_DIMENSION,
                maxPixels = MAX_ANIMATED_SOURCE_PIXELS
            ) && fileBytes in 1L..MAX_ANIMATED_FILE_BYTES
            if (fitsMovieBudget) {
                if (!hasAnimatedGifFrameBudget(file, MAX_ANIMATED_FRAME_COUNT)) return null
                return decodeStreamedGif(file) ?: decodeMovie(file)
            }
            if (!isHighResolutionSpritePath(path)) return null
            decodeStreamedHdGif(file, canvasSize)
        } else {
            if (isHighResolutionSpritePath(path)) return null
            BitmapFactory.decodeFile(file.path)?.let(SpriteAsset::fromBitmap)
        }
    }

    private fun decodeMovie(file: File): SpriteAsset? {
        return Movie.decodeFile(file.path)?.takeIf {
            it.width() > 0 && it.height() > 0 && it.duration() > 0 && hasDistinctMovieFrames(it)
        }?.let(SpriteAsset::fromMovie)
    }

    private fun decodeStreamedGif(file: File): SpriteAsset? {
        val gif = ShowdownStreamingGif.fromFile(
            file = file,
            maxFrameDimension = MAX_ANIMATED_FRAME_DIMENSION,
            maxSourceDimension = MAX_ANIMATED_SOURCE_DIMENSION,
            maxSourcePixels = MAX_ANIMATED_SOURCE_PIXELS,
            maxFileBytes = MAX_ANIMATED_FILE_BYTES,
            maxFrames = MAX_ANIMATED_FRAME_COUNT
        )
        return gif?.let(SpriteAsset::fromStreamingGif)
    }

    private fun decodeStreamedHdGif(file: File, canvasSize: Pair<Int, Int>): SpriteAsset? {
        if (!animatedGifFitsDecodeBudget(
                sourceWidth = canvasSize.first,
                sourceHeight = canvasSize.second,
                maxDimension = MAX_STREAMED_HD_SOURCE_DIMENSION,
                maxPixels = MAX_STREAMED_HD_SOURCE_PIXELS
            ) || file.length() !in 1L..MAX_STREAMED_HD_FILE_BYTES
        ) return null
        val gif = ShowdownStreamingGif.fromFile(
            file = file,
            maxFrameDimension = MAX_ANIMATED_FRAME_DIMENSION,
            maxSourceDimension = MAX_STREAMED_HD_SOURCE_DIMENSION,
            maxSourcePixels = MAX_STREAMED_HD_SOURCE_PIXELS,
            maxFileBytes = MAX_STREAMED_HD_FILE_BYTES,
            maxFrames = MAX_STREAMED_HD_FRAME_COUNT
        )
        return gif?.let(SpriteAsset::fromStreamingGif)
    }

    private fun readGifCanvasSize(file: File): Pair<Int, Int>? {
        return runCatching {
            file.inputStream().use { input ->
                val header = ByteArray(13)
                var offset = 0
                while (offset < header.size) {
                    val count = input.read(header, offset, header.size - offset)
                    if (count <= 0) return@use null
                    offset += count
                }
                gifCanvasSize(header)
            }
        }.getOrNull()
    }

    private fun hasDistinctMovieFrames(movie: Movie): Boolean {
        val movieWidth = movie.width()
        val movieHeight = movie.height()
        val duration = movie.duration()
        if (movieWidth <= 0 || movieHeight <= 0 || duration <= 0) return false
        val sampleWidth = movieWidth.coerceAtMost(128)
        val sampleHeight = movieHeight.coerceAtMost(128)
        val sample = Bitmap.createBitmap(sampleWidth, sampleHeight, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sample)
        val pixels = IntArray(sampleWidth * sampleHeight)
        val sampleTimes = (0..4).map { index ->
            (duration.toLong() * index / 4L).toInt().coerceAtMost(duration - 1)
        }.distinct()
        return try {
            var firstFrame: IntArray? = null
            for (time in sampleTimes) {
                sample.eraseColor(0)
                movie.setTime(time)
                canvas.save()
                canvas.scale(sampleWidth / movieWidth.toFloat(), sampleHeight / movieHeight.toFloat())
                movie.draw(canvas, 0f, 0f)
                canvas.restore()
                sample.getPixels(pixels, 0, sampleWidth, 0, 0, sampleWidth, sampleHeight)
                val currentFrame = pixels.copyOf()
                if (firstFrame != null && !firstFrame.contentEquals(currentFrame)) return true
                firstFrame = currentFrame
            }
            false
        } finally {
            sample.recycle()
        }
    }

    private fun downloadTo(file: File, path: String): Boolean {
        val url = if (path.startsWith("https://")) path else "https://play.pokemonshowdown.com/$path"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Showdown-Android/0.1")
        }
        val temporary = File(file.parentFile, "${file.name}.part")
        temporary.delete()
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.contentLength > MAX_FILE_BYTES) return false
            connection.inputStream.use { input ->
                FileOutputStream(temporary).use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0L
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count.toLong()
                        if (total > MAX_FILE_BYTES) {
                            temporary.delete()
                            return false
                        }
                        output.write(buffer, 0, count)
                    }
                }
            }
            if (!temporary.renameTo(file)) {
                temporary.delete()
                false
            } else {
                true
            }
        } finally {
            connection.disconnect()
        }
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
        const val SPRITE_MEMORY_CACHE_BYTES = 12 * 1024 * 1024
        const val MAX_FILE_BYTES = 16 * 1024 * 1024
        const val MAX_DISK_BYTES = 256L * 1024L * 1024L
    }
}
