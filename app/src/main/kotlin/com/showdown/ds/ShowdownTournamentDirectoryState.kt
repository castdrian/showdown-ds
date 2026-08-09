package com.showdown.ds

class ShowdownTournamentDirectoryState {
    data class TournamentSummary(
        val roomId: String,
        val roomName: String,
        val format: String,
        val generator: String,
        val started: Boolean,
        val playerCount: Int?
    )

    data class Snapshot(
        val title: String,
        val text: String,
        val tournaments: List<TournamentSummary>,
        val error: String?
    )

    private var current = Snapshot("Tournaments", "Loading tournaments…", emptyList(), null)

    val snapshot: Snapshot
        get() = current

    fun clear() {
        current = Snapshot("Tournaments", "Loading tournaments…", emptyList(), null)
    }

    fun applyProtocol(roomId: String?, lines: List<String>): Boolean {
        if (roomId?.startsWith("view-tournaments") != true) return false
        var changed = false
        lines.forEach { line ->
            when {
                line.startsWith("|title|") -> {
                    current = current.copy(title = line.removePrefix("|title|").trim().ifBlank { "Tournaments" }, error = null)
                    changed = true
                }
                line.startsWith("|pagehtml|") -> {
                    current = current.copy(
                        text = toReadableText(line.removePrefix("|pagehtml|")),
                        tournaments = parseTournaments(line.removePrefix("|pagehtml|")),
                        error = null
                    )
                    changed = true
                }
                line.startsWith("|error|") -> {
                    current = current.copy(error = line.removePrefix("|error|").trim(), text = "", tournaments = emptyList())
                    changed = true
                }
            }
        }
        return changed
    }

    private fun parseTournaments(html: String): List<TournamentSummary> {
        val instantSection = html.substringAfter("<h2>Instant Tournaments</h2>", "")
        val section = instantSection.substringBefore("</div>", instantSection)
        return Regex("<a[^>]+href=\"/([^\"]+)\"[^>]*class=\"blocklink\"[^>]*>(.*?)</a>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
            .findAll(section)
            .mapNotNull { match ->
                val roomId = match.groupValues[1].trim().lowercase()
                val htmlEntry = match.groupValues[2]
                val text = toReadableText(htmlEntry)
                if (roomId.isBlank() || text.isBlank()) return@mapNotNull null
                val roomName = Regex("<strong>(.*?)</strong>", RegexOption.IGNORE_CASE)
                    .find(htmlEntry)?.groupValues?.getOrNull(1)?.let(::toReadableText).orEmpty()
                val lines = text.lines().map(String::trim).filter(String::isNotBlank)
                val details = lines.drop(1).joinToString(" ")
                val playerCount = Regex("\\((\\d+) players?\\)", RegexOption.IGNORE_CASE)
                    .find(text)?.groupValues?.getOrNull(1)?.toIntOrNull()
                val started = section.substringBefore(match.value).takeLast(160).contains("<strong>Started:</strong>", true) ||
                    text.contains("Started", true)
                val format = details.substringBeforeLast(' ').trim().ifBlank { details }
                val generator = details.substringAfterLast(' ').trim()
                TournamentSummary(roomId, roomName.ifBlank { roomId }, format, generator, started, playerCount)
            }
            .toList()
    }

    private fun toReadableText(html: String): String = html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("</(?:p|h[1-6]|div|li|form|hr|tr)>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
        .replace("&quot;", "\"")
        .replace("&rarr;", "→")
        .replace("&larr;", "←")
        .replace("&ndash;", "–")
        .replace("&mdash;", "—")
        .replace("&laquo;", "«")
        .replace("&raquo;", "»")
        .replace("&amp;apos;", "'")
        .replace("&amp;laquo;", "«")
        .replace("&amp;raquo;", "»")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n[ \\t]+"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    companion object {
        fun pageCommand() = "/join view-tournaments-all"
        fun joinCommand(roomId: String) = "/join ${roomId.trim()}"
    }
}
