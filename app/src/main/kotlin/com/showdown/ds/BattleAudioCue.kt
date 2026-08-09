package com.showdown.ds

enum class BattleAudioCue(val assetName: String) {
    GENERIC_DAMAGE("hitnormaldamage"),
    SUPER_EFFECTIVE("hitsupereffective"),
    NOT_VERY_EFFECTIVE("hitweaknotveryeffective"),
    STAT_BOOST("statriseup"),
    STAT_DROP("statfalldown")
}

object BattleAudioCueResolver {
    fun cueForProtocolLine(line: String): BattleAudioCue? = when {
        line.startsWith("|move|") -> BattleAudioCue.GENERIC_DAMAGE
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
}
