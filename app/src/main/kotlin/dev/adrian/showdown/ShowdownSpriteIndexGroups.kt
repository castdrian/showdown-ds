package dev.adrian.showdown

object ShowdownSpriteIndexGroups {
    private val groupLinkPattern = Regex(
        """href=[\"'](?:[^\"']*/)?sprites_pokemon(?:_variocolores)?(?:_espalda)?\.php\?cid=(\d+)[^\"']*[\"']""",
        RegexOption.IGNORE_CASE
    )

    fun pageUrls(html: String, indexUrl: String): List<String> {
        val pageBase = indexUrl.substringBefore('?')
        return groupLinkPattern.findAll(html)
            .map { it.groupValues[1].toInt() }
            .distinct()
            .sorted()
            .map { cid -> "$pageBase?cid=$cid&order=#sprites" }
            .toList()
    }
}
