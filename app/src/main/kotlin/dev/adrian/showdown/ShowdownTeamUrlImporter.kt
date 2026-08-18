package dev.adrian.showdown

import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

data class ShowdownTeamUrlPayload(
    val text: String,
    val name: String = "Imported team",
    val format: String = "gen9"
)

object ShowdownTeamUrlImporter {
    fun intentSource(action: String?, data: String?, sharedText: String?): String? {
        val source = when (action) {
            Intent.ACTION_VIEW -> data
            Intent.ACTION_SEND -> sharedText
            else -> null
        }?.trim()?.takeIf(String::isNotBlank) ?: return null
        return source.takeIf { normalize(it) != null || isLikelyTeamText(it) }
    }

    fun normalize(input: String): String? {
        val uri = runCatching { URI(input.trim()) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val segments = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
        return when {
            host == "pokepast.es" || host == "www.pokepast.es" -> {
                val pasteId = segments.firstOrNull()?.lowercase(Locale.ROOT)
                pasteId?.takeIf { it.matches(Regex("[a-z0-9]+")) }?.let { "https://pokepast.es/$it/json" }
            }
            host == "gist.github.com" || host == "gist.githubusercontent.com" -> {
                val user = segments.getOrNull(0)
                val gistId = segments.getOrNull(1)
                if (user.isNullOrBlank() || gistId.isNullOrBlank()) null
                else "https://gist.githubusercontent.com/$user/$gistId/raw"
            }
            host == "psim.us" && segments.firstOrNull()?.equals("t", true) == true -> {
                val shareId = segments.getOrNull(1)
                shareId?.takeIf { it.matches(Regex("[0-9]+(?:-[a-z0-9]+)?", RegexOption.IGNORE_CASE)) }
                    ?.let { "https://psim.us/t/$it" }
            }
            else -> null
        }
    }

    fun isLikelyTeamText(value: String): Boolean {
        val text = value.trim()
        return text.isNotBlank() && (
            normalize(text) != null ||
                text.contains("===") ||
                text.contains("]") && text.contains("|") ||
                text.contains("\n-") ||
                text.contains(" @ ") ||
                text.contains("Ability:", true) ||
                text.contains("EVs:", true) ||
                text.contains("IVs:", true) ||
                text.contains("Tera Type:", true)
            )
    }

    fun payload(body: String, fallbackName: String = "Imported team", fallbackFormat: String = "gen9"): ShowdownTeamUrlPayload {
        val json = runCatching { JSONObject(body.trim()) }.getOrNull()
        val html = ShowdownTeamRemotePage.payload(body)
        val text = json?.optString("paste")?.takeIf(String::isNotBlank) ?: html?.text ?: body
        val notes = json?.optString("notes").orEmpty()
        val name = json?.optString("title")?.trim()
            ?.takeUnless { it.isNullOrBlank() || it.startsWith("Untitled", true) }
            ?.replace(Regex("[|\\\\/]"), "")
            ?: html?.name?.takeUnless(String::isBlank)
            ?: fallbackName
        val format = notes.lineSequence()
            .firstOrNull { it.trim().startsWith("Format:", true) }
            ?.substringAfter(':')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?.filter(Char::isLetterOrDigit)
            ?.takeIf(String::isNotBlank)
            ?: html?.formatLabel?.let(ShowdownTeamRemoteState::formatIdFromLabel)
            ?: fallbackFormat
        return ShowdownTeamUrlPayload(text.replace("\r\n", "\n"), name, format)
    }
}

class ShowdownTeamUrlFetcher : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetch(source: String, callback: (Result<ShowdownTeamUrlPayload>) -> Unit) {
        val normalized = ShowdownTeamUrlImporter.normalize(source)
        if (normalized == null) {
            mainHandler.post { callback(Result.failure(IllegalArgumentException("Unsupported team URL"))) }
            return
        }
        executor.execute {
            val result = runCatching { ShowdownTeamUrlImporter.payload(download(normalized)) }
            mainHandler.post { callback(result) }
        }
    }

    override fun close() {
        executor.shutdownNow()
        mainHandler.removeCallbacksAndMessages(null)
    }

    private fun download(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 8000
            readTimeout = 12000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "Showdown-Android/0.1")
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK || connection.contentLength > MAX_BYTES) {
                throw IllegalStateException("The team URL could not be downloaded")
            }
            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IllegalStateException("The team URL is too large")
                        output.write(buffer, 0, count)
                    }
                    output.toString(Charsets.UTF_8.name())
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    companion object {
        private const val MAX_BYTES = 1_000_000
    }
}
