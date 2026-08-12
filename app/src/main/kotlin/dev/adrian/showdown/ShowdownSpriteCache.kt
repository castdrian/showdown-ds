package dev.adrian.showdown

import android.annotation.TargetApi
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ImageDecoder
import android.graphics.Movie
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.drawable.AnimatedImageDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import kotlin.math.min

class ShowdownSpriteCache(context: Context) : AutoCloseable {
    class SpriteAsset private constructor(
        private val bitmap: Bitmap?,
        private val movie: Movie?,
        private val animatedDrawable: AnimatedImageDrawable?,
        private val width: Int,
        private val height: Int
    ) {
        val isAnimated get() = movie != null || animatedDrawable != null

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
                canvas.drawBitmap(bitmap, Rect(0, 0, width, height), RectF(left, top, left + drawWidth, top + drawHeight), null)
                if (alpha < 255) canvas.restore()
                canvas.restore()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                animatedDrawable?.let {
                    it.bounds = Rect(left.toInt(), top.toInt(), (left + drawWidth).toInt(), (top + drawHeight).toInt())
                    if (!it.isRunning) it.start()
                    it.draw(canvas)
                    if (alpha < 255) canvas.restore()
                    canvas.restore()
                    return
                }
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
            fun fromBitmap(bitmap: Bitmap) = SpriteAsset(bitmap, null, null, bitmap.width, bitmap.height)

            fun fromMovie(movie: Movie) = SpriteAsset(null, movie, null, movie.width(), movie.height())

            @TargetApi(Build.VERSION_CODES.P)
            fun fromAnimatedDrawable(drawable: AnimatedImageDrawable): SpriteAsset {
                drawable.repeatCount = AnimatedImageDrawable.REPEAT_INFINITE
                return SpriteAsset(null, null, drawable, drawable.intrinsicWidth, drawable.intrinsicHeight)
            }
        }
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private val downloadExecutor = Executors.newFixedThreadPool(2)
    private val memoryCache = LruCache<String, SpriteAsset>(16)
    private val pendingSpriteReceivers = ConcurrentHashMap<String, MutableList<(SpriteAsset?) -> Unit>>()
    private val pendingFileReceivers = ConcurrentHashMap<String, MutableList<(File?) -> Unit>>()
    private val diskCache = File(context.cacheDir, "showdown-resources").apply { mkdirs() }

    fun requestPokemon(request: BattleSpriteRequest, receiver: (SpriteAsset?) -> Unit) {
        requestSpriteCandidates(ShowdownAssetPaths.battleSpriteCandidates(request), receiver)
    }

    fun requestDexSprite(species: String, receiver: (SpriteAsset?) -> Unit) {
        requestSprite(ShowdownAssetPaths.dexSprite(species), receiver)
    }

    fun requestPlaceholder(side: BattleSpriteSide, receiver: (SpriteAsset?) -> Unit) {
        requestSprite(ShowdownAssetPaths.placeholder(side), receiver)
    }

    fun requestTrainer(trainer: String, receiver: (SpriteAsset?) -> Unit) {
        requestSprite(ShowdownAssetPaths.trainer(trainer)) { receiver(it?.trimHorizontalTransparentPadding()) }
    }

    fun requestBackdrop(name: String = "bg-aquacordetown.jpg", receiver: (Bitmap?) -> Unit) {
        requestBytes("sprites/gen6bgs/$name") { file ->
            receiver(file?.let { BitmapFactory.decodeFile(it.path) })
        }
    }

    fun requestEffect(name: String, receiver: (Bitmap?) -> Unit) {
        requestBytes("fx/$name") { file ->
            receiver(file?.let { BitmapFactory.decodeFile(it.path) })
        }
    }

    fun requestPokemonBallSheet(receiver: (Bitmap?) -> Unit) {
        requestBytes("sprites/pokemonicons-pokeball-sheet.png") { file ->
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
        val extension = path.substringAfterLast('.', "bin")
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
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val source = ImageDecoder.createSource(file)
                (ImageDecoder.decodeDrawable(source) as? AnimatedImageDrawable)?.let(SpriteAsset::fromAnimatedDrawable)
            } else {
                Movie.decodeFile(file.path)?.takeIf { it.width() > 0 && it.height() > 0 }?.let(SpriteAsset::fromMovie)
            }
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
        const val MAX_FILE_BYTES = 8 * 1024 * 1024
        const val MAX_DISK_BYTES = 96L * 1024L * 1024L
    }
}
