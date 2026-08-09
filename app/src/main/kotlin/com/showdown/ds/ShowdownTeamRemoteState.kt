package com.showdown.ds

class ShowdownTeamRemoteState {
    data class RemoteTeam(
        val remoteId: String,
        val privateKey: String?,
        val name: String,
        val formatLabel: String,
        val owner: String
    )

    data class Snapshot(
        val title: String,
        val text: String,
        val teams: List<RemoteTeam>,
        val selectedTeam: RemoteTeam?,
        val packed: String?,
        val error: String?
    )

    private var current = Snapshot("Remote teams", "Loading remote teams…", emptyList(), null, null, null)

    val snapshot: Snapshot
        get() = current

    fun clear() {
        current = Snapshot("Remote teams", "Loading remote teams…", emptyList(), null, null, null)
    }

    fun applyProtocol(roomId: String?, lines: List<String>): Boolean {
        if (roomId?.startsWith("view-teams-") != true) return false
        var changed = false
        lines.forEach { line ->
            when {
                line.startsWith("|title|") -> {
                    current = current.copy(title = line.removePrefix("|title|").trim().ifBlank { "Remote teams" }, error = null)
                    changed = true
                }
                line.startsWith("|pagehtml|") -> {
                    val html = line.removePrefix("|pagehtml|")
                    val readable = toReadableText(html)
                    val teams = parsePreviews(html)
                    val selected = teams.firstOrNull()
                    val packed = ShowdownTeamCodec.parse(readable).takeIf { it.isNotEmpty() }?.let(ShowdownTeamCodec::pack)
                    current = current.copy(
                        text = readable,
                        teams = teams,
                        selectedTeam = selected,
                        packed = packed,
                        error = null
                    )
                    changed = true
                }
                line.startsWith("|error|") -> {
                    current = current.copy(error = line.removePrefix("|error|").trim(), text = "", teams = emptyList(), selectedTeam = null, packed = null)
                    changed = true
                }
                line.startsWith("|popup|") -> {
                    current = current.copy(error = line.removePrefix("|popup|").trim(), text = "", teams = emptyList(), selectedTeam = null, packed = null)
                    changed = true
                }
            }
        }
        return changed
    }

    private fun parsePreviews(html: String): List<RemoteTeam> = Regex(
        "<strong>(.*?)</strong>.*?<small>Uploaded by:\\s*<strong>(.*?)</strong>.*?<small>.*?Format:\\s*(.*?)</small>.*?<a class=\\\"subtle\\\" href=\\\"/view-team-([0-9]+)(?:-([a-z0-9]+))?",
        setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)
    ).findAll(html).mapNotNull { match ->
        val remoteId = match.groupValues[4].takeIf(String::isNotBlank) ?: return@mapNotNull null
        RemoteTeam(
            remoteId = remoteId,
            privateKey = match.groupValues[5].takeIf(String::isNotBlank),
            name = toReadableText(match.groupValues[1]).ifBlank { "Untitled $remoteId" },
            formatLabel = toReadableText(match.groupValues[3]).ifBlank { "Unknown format" },
            owner = toReadableText(match.groupValues[2]).ifBlank { "Unknown user" }
        )
    }.distinctBy { it.remoteId }.toList()

    private fun toReadableText(html: String): String = html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("<hr\\s*/?>"), "\n\n")
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
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n[ \\t]+"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    companion object {
        fun ownTeamsCommand() = "/join view-teams-all"
        fun browseCommand() = "/join view-teams-browse"
        fun searchCommand(format: String, pokemon: String, moves: String, ability: String, generation: String): String =
            "/join view-teams-searchpublic---${format.trim()}--${pokemon.trim()}--${moves.trim()}--${ability.trim()}--${generation.trim()}"
        fun viewCommand(team: RemoteTeam) = "/join view-teams-view-${team.remoteId}${team.privateKey?.let { "-$it" }.orEmpty()}"
    }
}
