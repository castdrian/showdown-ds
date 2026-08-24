package dev.adrian.showdown

object ShowdownSpriteIndexGroups {
    private val groupLinkPattern = Regex(
        """href=[\"'](?:[^\"']*/)?sprites_pokemon(?:_variocolores)?(?:_espalda)?(?:_sin_bordes)?\.php\?cid=(\d+)[^\"']*[\"']""",
        RegexOption.IGNORE_CASE
    )
    private val groupRangePattern = Regex(
        """<a[^>]*class=[\"'][^\"']*grouplink[^\"']*[\"'][^>]*href=[\"'][^\"']*sprites_pokemon(?:_variocolores)?(?:_espalda)?(?:_sin_bordes)?\.php\?cid=(\d+)[^\"']*[\"'][^>]*>\s*([^<]+?)\s*</a>""",
        RegexOption.IGNORE_CASE
    )

    private data class GroupRange(val id: Int, val end: String)

    fun pageUrls(html: String, indexUrl: String): List<String> {
        val pageBase = indexUrl.substringBefore('?')
        return groupLinkPattern.findAll(html)
            .map { it.groupValues[1].toInt() }
            .distinct()
            .sorted()
            .map { cid -> "$pageBase?cid=$cid&order=#sprites" }
            .toList()
    }

    fun pageUrls(html: String, indexUrl: String, speciesNames: List<String>): List<String> {
        val pageBase = indexUrl.substringBefore('?')
        val groups = groupRangePattern.findAll(html)
            .mapNotNull { match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@mapNotNull null
                val bounds = match.groupValues[2].split(" to ", limit = 2)
                if (bounds.size != 2) return@mapNotNull null
                GroupRange(id, normalize(bounds[1]))
            }
            .toList()
        if (groups.isEmpty()) return emptyList()
        val selectedIds = speciesNames
            .map(::normalize)
            .filter(String::isNotEmpty)
            .map { species ->
                groups.firstOrNull { species <= it.end } ?: groups.last()
            }
            .map(GroupRange::id)
            .toSet()
        return groups
            .filter { it.id in selectedIds }
            .sortedBy(GroupRange::id)
            .map { group -> "$pageBase?cid=${group.id}&order=#sprites" }
    }

    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)
}
