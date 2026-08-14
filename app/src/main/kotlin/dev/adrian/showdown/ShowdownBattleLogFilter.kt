package dev.adrian.showdown

object ShowdownBattleLogFilter {
    private val blockTag = Regex(
        "(?i)</?(?:address|article|aside|blockquote|br|caption|dd|div|dl|dt|fieldset|figcaption|figure|footer|form|h[1-6]|header|hr|li|main|nav|ol|p|pre|section|summary|table|tbody|td|tfoot|th|thead|tr|ul)(?:\\s[^>]*)?>"
    )
    private val hiddenSmallLine = Regex(
        "(?is)^\\s*<small(?:\\s[^>]*)?>\\s*\\[[^]]*].*"
    )
    private val diagnosticLine = Regex(
        "(?:^|\\s)at (?:Battle|BattleLog)\\.|^Error parsing\\s*:|sanitizeHTML requires caja|https?://play\\.pokemonshowdown\\.com/js/",
        RegexOption.IGNORE_CASE
    )
    private val nonBattleControl = Regex(
        "Register an account to protect your ladder rating!",
        RegexOption.IGNORE_CASE
    )
    private val markupTag = Regex("<[^>]*>")
    private val interactiveControl = Regex("(?is)<button[^>]*>.*?</button>")
    private val repeatedWhitespace = Regex("\\s+")
    private val decodedEntity = Regex(
        "&(?:nbsp|amp|lt|gt|quot|apos|rarr|larr|ndash|mdash|hellip|bull|#39|#x27);",
        RegexOption.IGNORE_CASE
    )
    private val internalTextLine = Regex("^\\[(?:debug|\\d{1,2}:\\d{2})]", RegexOption.IGNORE_CASE)

    fun visibleEntries(value: String): List<String> {
        return value
            .replace(Regex("(?is)<(?:script|style)[^>]*>.*?</(?:script|style)>"), "")
            .replace(interactiveControl, "")
            .replace(blockTag, "\n")
            .split('\n')
            .filterNot(hiddenSmallLine::matches)
            .map { line ->
                line
                    .replace(markupTag, "")
                    .replace(decodedEntity) { match -> decodeEntity(match.value) }
                    .replace(repeatedWhitespace, " ")
                    .trim()
            }
            .filter { line ->
                line.isNotBlank() &&
                    !internalTextLine.containsMatchIn(line) &&
                    !nonBattleControl.containsMatchIn(line) &&
                    !diagnosticLine.containsMatchIn(line)
            }
    }

    private fun decodeEntity(entity: String): String = when (entity.lowercase()) {
        "&nbsp;" -> " "
        "&amp;" -> "&"
        "&lt;" -> "<"
        "&gt;" -> ">"
        "&quot;" -> "\""
        "&apos;", "&#39;", "&#x27;" -> "'"
        "&rarr;" -> "→"
        "&larr;" -> "←"
        "&ndash;" -> "–"
        "&mdash;" -> "—"
        "&hellip;" -> "…"
        "&bull;" -> "•"
        else -> entity
    }
}
