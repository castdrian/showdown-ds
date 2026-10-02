package dev.adrian.showdown

object ShowdownTeamLibraryOrder {
    fun move(
        teams: List<ShowdownTeam>,
        visibleTeamIds: List<String>,
        teamId: String,
        direction: Int
    ): List<ShowdownTeam> {
        val visibleIndex = visibleTeamIds.indexOf(teamId)
        val targetVisibleIndex = visibleIndex + direction.compareTo(0)
        if (visibleIndex < 0 || targetVisibleIndex !in visibleTeamIds.indices) return teams

        val sourceIndex = teams.indexOfFirst { it.id == teamId }
        val targetIndex = teams.indexOfFirst { it.id == visibleTeamIds[targetVisibleIndex] }
        if (sourceIndex < 0 || targetIndex < 0) return teams

        return ShowdownTeamOrder.move(teams, sourceIndex, targetIndex - sourceIndex)
    }

    fun save(teams: List<ShowdownTeam>, team: ShowdownTeam): List<ShowdownTeam> {
        val existingIndex = teams.indexOfFirst { it.id == team.id }
        if (existingIndex < 0) return teams + team
        return teams.toMutableList().apply { set(existingIndex, team) }
    }
}
