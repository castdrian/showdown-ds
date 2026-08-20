package dev.adrian.showdown

object ShowdownFormatSearch {
    fun searchText(format: BattleSession.MatchFormat): String = listOf(
        format.label,
        format.menuLabel,
        format.id
    ).joinToString(" ")

    fun filter(formats: List<BattleSession.MatchFormat>, query: String): List<BattleSession.MatchFormat> {
        val terms = query.trim().split(Regex("\\s+")).filter(String::isNotBlank)
        if (terms.isEmpty()) return formats
        return formats.filter { format ->
            val searchable = searchText(format)
            terms.all { term -> searchable.contains(term, ignoreCase = true) }
        }
    }
}
