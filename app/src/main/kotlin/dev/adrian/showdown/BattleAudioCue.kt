package dev.adrian.showdown

enum class BattleAudioCue(val assetName: String) {
    GENERIC_DAMAGE("hitnormaldamage"),
    SUPER_EFFECTIVE("hitsupereffective"),
    NOT_VERY_EFFECTIVE("hitweaknotveryeffective"),
    STAT_BOOST("statriseup"),
    STAT_DROP("statfalldown")
}

object BattleAudioCueResolver {
    fun cueForProtocolLine(line: String): BattleAudioCue? = when {
        line.startsWith("|-supereffective|") -> BattleAudioCue.SUPER_EFFECTIVE
        line.startsWith("|-resisted|") -> BattleAudioCue.NOT_VERY_EFFECTIVE
        line.startsWith("|-boost|") -> BattleAudioCue.STAT_BOOST
        line.startsWith("|-unboost|") -> BattleAudioCue.STAT_DROP
        line.startsWith("|-setboost|") -> line.substringAfterLast('|').toIntOrNull()?.let {
            when {
                it > 0 -> BattleAudioCue.STAT_BOOST
                it < 0 -> BattleAudioCue.STAT_DROP
                else -> null
            }
        }
        else -> null
    }

    fun cueForNativeValue(value: String): BattleAudioCue? = when (value) {
        "generic_damage" -> BattleAudioCue.GENERIC_DAMAGE
        "super_effective" -> BattleAudioCue.SUPER_EFFECTIVE
        "not_very_effective" -> BattleAudioCue.NOT_VERY_EFFECTIVE
        "stat_boost" -> BattleAudioCue.STAT_BOOST
        "stat_drop" -> BattleAudioCue.STAT_DROP
        else -> null
    }
}
