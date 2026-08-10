package dev.adrian.showdown

data class ShowdownTeamLibraryFilter(
    val query: String = "",
    val folder: String? = null
)

object ShowdownTeamLibraryQuery {
    fun folders(teams: List<ShowdownTeam>): List<String> = teams
        .map { it.folder.trim() }
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun filter(teams: List<ShowdownTeam>, filter: ShowdownTeamLibraryFilter): List<ShowdownTeam> {
        val terms = filter.query
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
        return teams.filter { team ->
            val folderMatches = filter.folder == null || team.folder.equals(filter.folder, true)
            val searchText = buildString {
                append(team.name)
                append(' ')
                append(team.format)
                append(' ')
                append(team.folder)
                append(' ')
                append(team.packed)
            }.lowercase()
            folderMatches && terms.all(searchText::contains)
        }
    }
}
