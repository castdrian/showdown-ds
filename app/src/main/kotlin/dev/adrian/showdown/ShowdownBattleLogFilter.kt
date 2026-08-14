package dev.adrian.showdown

object ShowdownBattleLogFilter {
    private val diagnosticLine = Regex(
        "(?:^|\\s)at (?:Battle|BattleLog)\\.|^Error parsing\\s*:|sanitizeHTML requires caja|https?://play\\.pokemonshowdown\\.com/js/",
        RegexOption.IGNORE_CASE
    )
    private val nonBattleControl = Regex(
        "Register an account to protect your ladder rating!",
        RegexOption.IGNORE_CASE
    )

    fun visibleEntries(value: String): List<String> {
        if (nonBattleControl.containsMatchIn(value)) return emptyList()
        return value
            .replace(Regex("(?i)<br\\s*/?>"), "\n")
            .split('\n')
            .map(String::trim)
            .filter { line ->
                line.isNotBlank() &&
                    !line.startsWith("<small>[", ignoreCase = true) &&
                    !diagnosticLine.containsMatchIn(line)
            }
    }
}
