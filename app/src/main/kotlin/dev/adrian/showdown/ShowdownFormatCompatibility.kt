package dev.adrian.showdown

import java.util.Locale

object ShowdownFormatCompatibility {
    private val legacyHdMatchupText = Regex("(?i)\\bHD[\\s_-]*matchup\\b")

    fun isLegacyHdMatchup(value: String?): Boolean = value
        ?.lowercase(Locale.ROOT)
        ?.filter(Char::isLetterOrDigit)
        ?.equals("hdmatchup", true) == true

    fun canonicalizeLegacyText(value: String): String = value.replace(
        legacyHdMatchupText,
        BattleSession.MatchFormat.GEN9_RANDOM.label
    )

    fun canonicalId(id: String?, label: String? = null): String? {
        val trimmed = id?.trim().orEmpty()
        return if (isLegacyHdMatchup(trimmed) || isLegacyHdMatchup(label)) {
            BattleSession.MatchFormat.GEN9_RANDOM.id
        } else {
            trimmed.lowercase(Locale.ROOT).takeIf(String::isNotBlank)
        }
    }

    fun canonical(format: BattleSession.MatchFormat): BattleSession.MatchFormat {
        if (isLegacyHdMatchup(format.id) || isLegacyHdMatchup(format.label) || isLegacyHdMatchup(format.menuLabel)) {
            return BattleSession.MatchFormat.GEN9_RANDOM
        }
        val id = format.id.trim()
        val label = format.label.trim().ifBlank { id }
        val menuLabel = format.menuLabel.trim().ifBlank { label }
        return format.copy(id = id, label = label, menuLabel = menuLabel)
    }
}
