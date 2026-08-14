package dev.adrian.showdown

data class ShowdownTeamLibraryFilter(
    val query: String = "",
    val folder: String? = null,
    val format: String? = null
)

object ShowdownTeamLibraryQuery {
    private val namedFormatLabels = mapOf(
        "ag" to "Anything Goes",
        "aaa" to "Almost Any Ability",
        "balancedhackmons" to "Balanced Hackmons",
        "battlefactory" to "Battle Factory",
        "doublesou" to "Doubles OU",
        "doublesuu" to "Doubles UU",
        "monotype" to "Monotype",
        "nationaldex" to "National Dex",
        "randombattle" to "Random Battle",
        "ubers" to "Ubers"
    )
    private val uppercaseFormatLabels = setOf("bh", "lc", "nu", "ou", "pu", "ru", "uu")

    fun folders(teams: List<ShowdownTeam>): List<String> = teams
        .map { it.folder.trim() }
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun formats(teams: List<ShowdownTeam>): List<String> = teams
        .map { it.format.trim() }
        .filter(String::isNotBlank)
        .distinctBy(String::lowercase)
        .sortedWith(String.CASE_INSENSITIVE_ORDER)

    fun displayFormat(format: String): String {
        val normalized = format.trim()
        if (normalized.isBlank()) return "Unknown format"
        val match = Regex("^gen(\\d+)([a-z0-9]+)$", RegexOption.IGNORE_CASE).matchEntire(normalized)
            ?: return normalized
        val suffix = match.groupValues[2].lowercase()
        val suffixLabel = namedFormatLabels[suffix]
            ?: if (suffix in uppercaseFormatLabels) suffix.uppercase() else suffix
        return "[Gen ${match.groupValues[1]}] $suffixLabel"
    }

    fun resolveFormat(format: String, knownFormats: Collection<BattleSession.MatchFormat>): BattleSession.MatchFormat? {
        val normalized = format.trim()
        return knownFormats.firstOrNull { it.id.trim().equals(normalized, true) }
    }

    fun matchFormat(format: String, knownFormats: Collection<BattleSession.MatchFormat>): BattleSession.MatchFormat {
        val normalized = format.trim()
        val advertised = resolveFormat(normalized, knownFormats)
        val readableLabel = displayFormat(normalized, knownFormats)
        return advertised?.copy(
            id = advertised.id.trim(),
            label = readableLabel,
            menuLabel = advertised.menuLabel.trim().takeUnless { it.isBlank() || it.equals(advertised.id.trim(), true) }
                ?: readableLabel
        ) ?: BattleSession.MatchFormat(normalized, readableLabel)
    }

    fun displayFormat(format: String, knownFormats: Collection<BattleSession.MatchFormat>): String {
        val advertised = resolveFormat(format, knownFormats)
        return advertised?.label?.trim()
            ?.takeUnless { it.isBlank() || it.equals(advertised.id.trim(), true) }
            ?: displayFormat(format)
    }

    fun filter(teams: List<ShowdownTeam>, filter: ShowdownTeamLibraryFilter): List<ShowdownTeam> {
        val terms = filter.query
            .split(',')
            .map(String::trim)
            .filter(String::isNotBlank)
            .map(String::lowercase)
        val folder = filter.folder?.trim()
        val format = filter.format?.trim()
        return teams.filter { team ->
            val folderMatches = folder == null || team.folder.trim().equals(folder, true)
            val formatMatches = format == null || team.format.trim().equals(format, true)
            val searchText = buildString {
                append(team.name)
                append(' ')
                append(team.format)
                append(' ')
                append(team.folder)
                append(' ')
                append(team.packed)
            }.lowercase()
            folderMatches && formatMatches && terms.all(searchText::contains)
        }
    }

    fun matchingFormat(teams: List<ShowdownTeam>, format: String): List<ShowdownTeam> = filter(
        teams,
        ShowdownTeamLibraryFilter(format = format)
    )
}
