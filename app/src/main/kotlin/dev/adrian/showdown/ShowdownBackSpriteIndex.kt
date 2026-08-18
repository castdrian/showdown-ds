package dev.adrian.showdown

object ShowdownBackSpriteIndex {
    private val imagePathPattern = Regex(
        """(?:href|src)=[\"']([^\"']*/sprites/(?:animados-gigante|animados-espalda|animados)/[^\"']+\.gif)[\"']""",
        RegexOption.IGNORE_CASE
    )

    fun candidates(html: String, speciesNames: List<String>, shiny: Boolean = false): List<String> {
        val requestedNames = speciesNames.flatMap(::normalizedNames).toSet()
        return imagePathPattern.findAll(html).mapNotNull { match ->
            val path = match.groupValues[1].substringBefore('?')
            val spriteRoot = path.substringAfter("/sprites/").substringBefore('/')
            val fileName = path.substringAfterLast('/')
            val fileStem = fileName.dropLast(".gif".length)
            val isShiny = fileStem.endsWith("-s", ignoreCase = true)
            if (isShiny != shiny) return@mapNotNull null
            val nonShinyStem = if (isShiny) fileStem.dropLast(2) else fileStem
            val isBack = spriteRoot == "animados-espalda" || nonShinyStem.endsWith("-back", ignoreCase = true)
            if (!isBack) return@mapNotNull null
            val spriteName = nonShinyStem.removeSuffix("-back")
            if (requestedNames.contains(normalize(spriteName))) absoluteUrl(path) else null
        }.distinct().sortedWith(compareBy { path -> if (path.contains("/animados-gigante/")) 0 else 1 }).toList()
    }

    private fun normalizedNames(value: String): List<String> = listOf(value, value.replace('-', ' ')).map(::normalize).distinct()

    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun absoluteUrl(path: String): String = when {
        path.startsWith("https://", ignoreCase = true) -> path
        path.startsWith("//") -> "https:$path"
        path.startsWith('/') -> "https://www.pkparaiso.com$path"
        else -> "https://www.pkparaiso.com/$path"
    }
}
