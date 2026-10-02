package dev.adrian.showdown

import java.util.Locale

object ShowdownStatPresentation {
    private val statLabels = listOf(
        "atk" to "Atk",
        "def" to "Def",
        "spa" to "SpA",
        "spd" to "SpD",
        "spe" to "Spe"
    )

    data class BaseStats(
        val hp: Int,
        val attack: Int,
        val defense: Int,
        val specialAttack: Int,
        val specialDefense: Int,
        val speed: Int
    ) {
        val total = hp + attack + defense + specialAttack + specialDefense + speed
    }

    fun exactStats(stats: Map<String, Int>, generation: Int = 9): String {
        val labels = if (generation == 1) {
            listOf("atk" to "Atk", "def" to "Def", "spc" to "Spc", "spe" to "Spe")
        } else {
            statLabels
        }
        return labels.mapNotNull { (id, label) ->
            val value = if (id == "spc") stats["spc"] ?: stats["spa"] ?: stats["spd"] else stats[id]
            value?.takeIf { it >= 0 }?.let { "$label $it" }
        }.joinToString(" · ")
    }

    fun speedRange(
        baseSpeed: Int,
        level: Int,
        generation: Int,
        randomBattle: Boolean,
        format: String = ""
    ) = speedRange(BaseStats(0, 0, 0, 0, 0, baseSpeed), level, generation, randomBattle, format)

    fun speedRange(
        baseStats: BaseStats,
        level: Int,
        generation: Int,
        randomBattle: Boolean,
        format: String = ""
    ): String {
        if (baseStats.speed <= 0) return ""
        val normalizedFormat = format.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)
        val computerGeneratedTeams = "computergeneratedteams" in normalizedFormat
        var baseSpeed = baseStats.speed
        when {
            "franticfusions" in normalizedFormat -> return "Speed range unavailable for this format"
            "scalemons" in normalizedFormat -> {
                val scaleBase = baseStats.total - baseStats.hp
                if (scaleBase <= 0) return "Speed range unavailable for this format"
                baseSpeed = (baseSpeed * (600 - baseStats.hp) / scaleBase).coerceIn(1, 255)
            }
            "flipped" in normalizedFormat -> baseSpeed = baseStats.hp.coerceIn(1, 255)
            "350cup" in normalizedFormat && baseStats.total <= 350 -> baseSpeed = (baseSpeed * 2).coerceIn(1, 255)
        }
        val safeLevel = level.coerceAtLeast(1)
        if (generation < 3) {
            val maximum = statWithNature(baseSpeed, safeLevel, 30, 63, 1.0)
            val minimum = if (computerGeneratedTeams) maximum else statWithNature(baseSpeed, safeLevel, 0, 0, 1.0)
            val estimate = if (randomBattle) "Spe $maximum" else "Spe $minimum to $maximum"
            return "$estimate (before stat stage changes)"
        }

        val natureMinimum = if (randomBattle) 1.0 else 0.9
        val natureMaximum = if (randomBattle) 1.0 else 1.1
        if ("letsgo" in normalizedFormat) {
            val minimum = letsGoStat(baseSpeed, safeLevel, 0, 0, natureMinimum)
            val ev0 = letsGoStat(baseSpeed, safeLevel, 31, 0, 1.0)
            val ev84 = ev0
            val maximum = letsGoStat(baseSpeed, safeLevel, 31, 0, natureMaximum) + when {
                "norestrictions" in normalizedFormat -> 200
                randomBattle -> 20
                else -> 0
            }
            val estimate = if (randomBattle) "Spe $minimum or $ev84" else "Spe $minimum–$ev0–$maximum"
            return "$estimate (before external modifiers)"
        }
        if ("champions" in normalizedFormat) {
            val minimum = (natureMinimum * (baseSpeed + 20)).toInt()
            val ev0 = ((2 * baseSpeed + 31) * safeLevel / 100.0).toInt() + 5
            val ev84 = ((2 * baseSpeed + 31 + 21) * safeLevel / 100.0).toInt() + 5
            val ev252 = baseSpeed + 32 + 20
            val maximum = (natureMaximum * ev252).toInt()
            val estimate = if (randomBattle) "Spe $minimum or $ev84" else "Spe $minimum–$ev0–$ev252–$maximum"
            return "$estimate (before external modifiers)"
        }
        val ev0 = statWithNature(baseSpeed, safeLevel, 31, 0, 1.0)
        val ev84 = statWithNature(baseSpeed, safeLevel, 31, 21, 1.0)
        val ev252 = statWithNature(baseSpeed, safeLevel, 31, 63, 1.0)
        val maximum = (ev252 * natureMaximum).toInt()
        val minimum = if (computerGeneratedTeams) maximum else statWithNature(baseSpeed, safeLevel, 0, 0, natureMinimum)
        val estimate = if (randomBattle) "Spe $minimum or $ev84" else "Spe $minimum–$ev0–$ev252–$maximum"
        return "$estimate (before external modifiers)"
    }

    private fun letsGoStat(baseSpeed: Int, level: Int, iv: Int, ev: Int, nature: Double): Int {
        val base = ((2 * baseSpeed + iv + ev) * level / 100.0 + 5).toInt()
        val natureAdjusted = (base * nature).toInt()
        return (natureAdjusted * 1.02).toInt()
    }

    private fun statWithNature(
        baseSpeed: Int,
        level: Int,
        individualValueContribution: Int,
        effortValueContribution: Int,
        nature: Double
    ): Int {
        val base = ((2 * baseSpeed + individualValueContribution + effortValueContribution) * level / 100.0 + 5).toInt()
        return (base * nature).toInt()
    }
}
