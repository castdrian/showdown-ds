package dev.adrian.showdown

object ShowdownFrontSpriteIndex {
    private val imagePathPattern = Regex(
        """(?:(?:https?:)?//www\.pkparaiso\.com)?/?imagenes/[^"']*/sprites/(?:animados-gigante|animados-sinbordes-gigante|animados-sinbordes|animados-shiny|animados)/[^"']+\.gif""",
        RegexOption.IGNORE_CASE
    )

    fun candidates(html: String, speciesNames: List<String>, shiny: Boolean = false): List<String> {
        val requestedNames = speciesNames.flatMap(::normalizedNames).toSet()
        return imagePathPattern.findAll(html).mapNotNull { match ->
            val path = match.value.substringBefore('?')
            val spriteRoot = path.substringAfter("/sprites/").substringBefore('/')
            val fileName = path.substringAfterLast('/')
            val fileStem = fileName.dropLast(".gif".length)
            val isShiny = fileStem.endsWith("-s", ignoreCase = true) || spriteRoot.equals("animados-shiny", ignoreCase = true)
            if (isShiny != shiny) return@mapNotNull null
            val nonShinyStem = if (fileStem.endsWith("-s", ignoreCase = true)) fileStem.dropLast(2) else fileStem
            if (nonShinyStem.endsWith("-back", ignoreCase = true)) return@mapNotNull null
            if (requestedNames.contains(normalize(nonShinyStem))) absoluteUrl(path) else null
        }.distinct().sortedWith(compareBy { path ->
            when {
                path.contains("/animados-gigante/") || path.contains("/animados-sinbordes-gigante/") -> 0
                else -> 1
            }
        }).toList()
    }

    fun highResolutionCandidates(html: String, speciesNames: List<String>, shiny: Boolean = false): List<String> =
        candidates(html, speciesNames, shiny).filter(::isHighResolution)

    private fun normalizedNames(value: String): List<String> = listOf(value, value.replace('-', ' ')).map(::normalize).distinct()

    private fun normalize(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun isHighResolution(path: String) =
        path.contains("/sprites/animados", ignoreCase = true)

    private fun absoluteUrl(path: String): String = when {
        path.startsWith("https://", ignoreCase = true) -> path
        path.startsWith("http://", ignoreCase = true) -> path.replaceFirst("http://", "https://", ignoreCase = true)
        path.startsWith("//") -> "https:$path"
        path.startsWith('/') -> "https://www.pkparaiso.com$path"
        else -> "https://www.pkparaiso.com/$path"
    }
}
