package dev.adrian.showdown

import java.util.Locale

data class ShowdownLadderBadge(
    val type: String,
    val format: String,
    val threshold: String,
    val season: String
) {
    val assetPath: String
        get() = "sprites/misc/${formatType(format)}_${type}.png"

    val accessibilityLabel: String
        get() = "User is Top $threshold on the $format Ladder"

    companion object {
        private val formatIdPattern = Regex("[a-z0-9]+")
        private val generationPattern = Regex("^gen\\d+")
        private val badgeDetailsPattern = Regex("^(\\d+)-(\\d+)$")

        fun fromProtocolValue(value: String): ShowdownLadderBadge? {
            val fields = value.split('|')
            if (fields.size != 3) return null
            val type = fields[0].lowercase(Locale.ROOT)
            val format = fields[1].lowercase(Locale.ROOT)
            if (!type.matches(formatIdPattern) || !format.matches(formatIdPattern)) return null
            val details = badgeDetailsPattern.matchEntire(fields[2]) ?: return null
            return ShowdownLadderBadge(
                type = type,
                format = format,
                threshold = details.groupValues[1],
                season = details.groupValues[2]
            )
        }

        private fun formatType(format: String): String {
            val generation = generationPattern.find(format) ?: return "none"
            val suffix = format.removeRange(generation.range)
            return when (suffix) {
                "ou", "randombattle" -> suffix
                else -> "rotating"
            }
        }
    }
}

object ShowdownLadderBadgePresentation {
    private const val MAX_VISIBLE_BADGES = 3

    fun visible(badges: List<ShowdownLadderBadge>) = badges.take(MAX_VISIBLE_BADGES)

    fun forStatusCard(
        side: String,
        previouslyShownSides: Set<String>,
        badgesBySide: Map<String, List<ShowdownLadderBadge>>
    ) = if (side in previouslyShownSides) {
        emptyList()
    } else {
        visible(badgesBySide[side].orEmpty())
    }
}
