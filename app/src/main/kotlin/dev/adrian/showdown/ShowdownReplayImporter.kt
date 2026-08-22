package dev.adrian.showdown

import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.Executors

data class ShowdownReplayPayload(
    val id: String,
    val format: String,
    val players: List<String>,
    val log: String
) {
    val title get() = "[${format.ifBlank { "Replay" }}] ${players.joinToString(" vs. ").ifBlank { id }}"
}

data class ShowdownReplaySearchQuery(
    val user: String = "",
    val opponent: String = "",
    val format: String = "",
    val before: Long? = null
) {
    fun normalized(): ShowdownReplaySearchQuery = copy(
        user = user.trim(),
        opponent = opponent.trim(),
        format = format.trim().lowercase(Locale.ROOT),
        before = before?.takeIf { it > 0L }
    )
}

data class ShowdownReplaySearchEntry(
    val id: String,
    val format: String,
    val playerOne: String,
    val playerTwo: String,
    val uploadedAt: Long,
    val rating: Int? = null
) {
    val title: String
        get() = listOf(playerOne, playerTwo).filter(String::isNotBlank).joinToString(" vs. ").ifBlank { id }
}

data class ShowdownReplaySearchPage(
    val entries: List<ShowdownReplaySearchEntry>,
    val hasMore: Boolean,
    val nextBefore: Long?
)

object ShowdownReplaySearch {
    private const val PAGE_SIZE = 50
    private const val SEARCH_URL = "https://replay.pokemonshowdown.com/search.json"

    fun url(query: ShowdownReplaySearchQuery): String {
        val normalized = query.normalized()
        val values = buildList {
            normalized.user.takeIf(String::isNotBlank)?.let { add("user" to it) }
            normalized.opponent.takeIf(String::isNotBlank)?.let { add("user2" to it) }
            normalized.format.takeIf(String::isNotBlank)?.let { add("format" to it) }
            normalized.before?.let { add("before" to it.toString()) }
        }
        return values.takeIf { it.isNotEmpty() }?.let { pairs ->
            SEARCH_URL + "?" + pairs.joinToString("&") { (key, value) ->
                "${encode(key)}=${encode(value)}"
            }
        } ?: SEARCH_URL
    }

    fun page(body: String): ShowdownReplaySearchPage {
        val root = body.trim()
        val values = when {
            root.startsWith("[") -> JSONArray(root)
            root.startsWith("{") -> {
                val objectRoot = JSONObject(root)
                objectRoot.optJSONArray("replays") ?: objectRoot.optJSONArray("results") ?: JSONArray()
            }
            else -> JSONArray()
        }
        val parsed = buildList {
            for (index in 0 until values.length()) {
                values.optJSONObject(index)?.let(::parseSearchEntry)?.takeIf { it.id.isNotBlank() }?.let(::add)
            }
        }
        val hasMore = parsed.size > PAGE_SIZE
        val visible = parsed.take(PAGE_SIZE)
        val nextBefore = parsed.getOrNull(PAGE_SIZE)?.uploadedAt
            ?: visible.lastOrNull()?.uploadedAt
        return ShowdownReplaySearchPage(visible, hasMore, nextBefore.takeIf { hasMore })
    }

    private fun parseSearchEntry(value: JSONObject): ShowdownReplaySearchEntry {
        val players = value.optJSONArray("players")?.let(::parsePlayerNames).orEmpty()
        return ShowdownReplaySearchEntry(
            id = value.optString("id").trim(),
            format = value.optString("format").trim(),
            playerOne = players.getOrNull(0) ?: value.optString("p1").trim(),
            playerTwo = players.getOrNull(1) ?: value.optString("p2").trim(),
            uploadedAt = value.optLong("uploadtime", 0L).takeIf { it > 0L } ?: value.optString("uploadtime").toLongOrNull() ?: 0L,
            rating = value.optInt("rating", 0).takeIf { it > 0 }
        )
    }

    private fun encode(value: String): String = URLEncoder.encode(value, Charsets.UTF_8.name())
}

object ShowdownReplayImporter {
    private val replayUrl = Regex(
        "https?://(?:replay\\.pokemonshowdown\\.com/[A-Za-z0-9-]+(?:\\.json)?|pokemonshowdown\\.com/replay/[A-Za-z0-9-]+(?:\\.json)?)",
        RegexOption.IGNORE_CASE
    )
    private val replayFormatId = Regex("gen\\d+[a-z0-9]*", RegexOption.IGNORE_CASE)

    fun uploadUrl(message: String): String? = replayUrl.find(message)?.value?.removeSuffix(".json")

    fun intentSource(action: String?, data: String?, sharedText: String?): String? = when (action) {
        Intent.ACTION_VIEW -> data
        Intent.ACTION_SEND -> sharedText
        else -> null
    }?.takeIf { normalize(it) != null }

    fun normalize(input: String): String? {
        val source = replayUrl.find(input)?.value ?: input.trim()
        val uri = runCatching { URI(source) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase(Locale.ROOT)
        if (scheme != "http" && scheme != "https") return null
        val host = uri.host?.lowercase(Locale.ROOT) ?: return null
        val path = uri.path.orEmpty().trim('/').split('/').filter(String::isNotBlank)
        val id = when {
            host == "replay.pokemonshowdown.com" -> path.firstOrNull()
            host == "pokemonshowdown.com" && path.firstOrNull() == "replay" -> path.getOrNull(1)
            else -> null
        }?.removeSuffix(".json")?.takeIf { it.matches(Regex("[a-z0-9-]+")) }
        return id?.let { "https://replay.pokemonshowdown.com/$it.json" }
    }

    fun payload(body: String, fallbackId: String = "replay"): ShowdownReplayPayload {
        val json = JSONObject(body.trim())
        val id = json.optString("id").trim().ifBlank { fallbackId }
        val format = json.optString("format").trim().ifBlank { json.optString("formatid").trim() }
        val players = json.optJSONArray("players")?.let(::parsePlayerNames).orEmpty()
        val log = when (val value = json.opt("log")) {
            is JSONArray -> buildList {
                for (index in 0 until value.length()) value.optString(index).takeIf(String::isNotBlank)?.let(::add)
            }.joinToString("\n")
            else -> value?.toString().orEmpty()
        }.replace("\r\n", "\n")
        if (log.isBlank()) throw IllegalArgumentException("Replay log is empty")
        return ShowdownReplayPayload(id, format, players, log)
    }

    fun matchFormat(
        replay: ShowdownReplayPayload,
        knownFormats: Collection<BattleSession.MatchFormat>
    ): BattleSession.MatchFormat? {
        val readableId = ShowdownTeamRemoteState.formatIdFromLabel(replay.format)
            .lowercase(Locale.ROOT)
            .takeIf { replayFormatId.matches(it) }
        val replayId = replayFormatId.find(replay.id)?.value?.lowercase(Locale.ROOT)
        return (readableId ?: replayId)?.let { ShowdownTeamLibraryQuery.matchFormat(it, knownFormats) }
    }
}

private fun parsePlayerNames(value: JSONArray) = buildList {
    for (index in 0 until value.length()) value.optString(index).trim().takeIf(String::isNotBlank)?.let(::add)
}

class ShowdownReplayFetcher : AutoCloseable {
    private val executor = Executors.newSingleThreadExecutor()
    private val mainHandler = Handler(Looper.getMainLooper())

    fun fetch(source: String, callback: (Result<ShowdownReplayPayload>) -> Unit) {
        val normalized = ShowdownReplayImporter.normalize(source)
        if (normalized == null) {
            mainHandler.post { callback(Result.failure(IllegalArgumentException("Unsupported replay URL"))) }
            return
        }
        executor.execute {
            val result = runCatching {
                ShowdownReplayImporter.payload(download(normalized), normalized.substringAfterLast('/').removeSuffix(".json"))
            }
            mainHandler.post { callback(result) }
        }
    }

    fun search(query: ShowdownReplaySearchQuery, callback: (Result<ShowdownReplaySearchPage>) -> Unit) {
        executor.execute {
            val result = runCatching { ShowdownReplaySearch.page(download(ShowdownReplaySearch.url(query))) }
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
                throw IllegalStateException("The replay could not be downloaded")
            }
            connection.inputStream.use { input ->
                ByteArrayOutputStream().use { output ->
                    val buffer = ByteArray(8192)
                    var total = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        total += count
                        if (total > MAX_BYTES) throw IllegalStateException("The replay is too large")
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
        private const val MAX_BYTES = 5_000_000
    }
}
