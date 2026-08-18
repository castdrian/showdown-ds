package dev.adrian.showdown

data class TeamHealthBarPresentation(
    val fraction: Float,
    val label: String
) {
    companion object {
        fun from(hp: String, condition: String): TeamHealthBarPresentation {
            val value = hp.substringBefore(' ')
            val fainted = hp.contains("fnt", true) || condition.contains("fnt", true)
            if (fainted) {
                val maximum = value.substringAfter('/', "").takeIf { it.toFloatOrNull() != null }
                return TeamHealthBarPresentation(0f, if (maximum == null) "0" else "0/$maximum")
            }
            val fraction = when {
                value.endsWith('%') -> (value.dropLast(1).toFloatOrNull()?.div(100f) ?: 0f)
                else -> {
                    val values = value.split('/', limit = 2)
                    val current = values.getOrNull(0)?.toFloatOrNull()
                    val maximum = values.getOrNull(1)?.toFloatOrNull()
                    if (current != null && maximum != null && maximum > 0f) current / maximum else 0f
                }
            }.coerceIn(0f, 1f)
            return TeamHealthBarPresentation(fraction, value)
        }
    }
}
