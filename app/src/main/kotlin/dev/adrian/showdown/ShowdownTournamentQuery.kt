package dev.adrian.showdown

object ShowdownTournamentQuery {
    fun matches(query: String, tournament: ShowdownTournamentDirectoryState.TournamentSummary): Boolean {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        val searchable = listOf(
            tournament.roomId,
            tournament.roomName,
            tournament.format,
            tournament.generator,
            if (tournament.started) "started" else "accepting signups",
            tournament.playerCount?.toString().orEmpty()
        ).joinToString(" ")
        return terms.all { searchable.contains(it, ignoreCase = true) }
    }
}
