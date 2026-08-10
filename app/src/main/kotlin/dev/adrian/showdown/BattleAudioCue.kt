package dev.adrian.showdown

enum class BattleAudioCue(
    val assetName: String,
    val playbackDurationMillis: Long,
    val previewHoldMillis: Long
) {
    GENERIC_DAMAGE("hitnormaldamage", 700L, 1_000L),
    SUPER_EFFECTIVE("hitsupereffective", 1_800L, 2_200L),
    NOT_VERY_EFFECTIVE("hitweaknotveryeffective", 400L, 800L),
    STAT_BOOST("statriseup", 2_000L, 2_400L),
    STAT_DROP("statfalldown", 2_250L, 2_600L)
}

object BattleAudioPreviewTiming {
    fun startOffsets(cues: List<BattleAudioCue>): List<Long> {
        var delayMillis = 0L
        return cues.map { cue ->
            val startOffset = delayMillis
            delayMillis += cue.previewHoldMillis
            startOffset
        }
    }

    fun completionDelay(cues: List<BattleAudioCue>): Long = cues.sumOf { it.previewHoldMillis }
}

object BattleAudioCueResolver {
    fun cueForProtocolLine(line: String): BattleAudioCue? {
        val fields = line.split('|')
        return when (fields.getOrNull(1)) {
            "-supereffective" -> BattleAudioCue.SUPER_EFFECTIVE
            "-resisted" -> BattleAudioCue.NOT_VERY_EFFECTIVE
            "-boost" -> statCue(fields.getOrNull(4)?.toIntOrNull(), BattleAudioCue.STAT_BOOST, BattleAudioCue.STAT_DROP)
            "-unboost" -> statCue(fields.getOrNull(4)?.toIntOrNull(), BattleAudioCue.STAT_DROP, BattleAudioCue.STAT_BOOST)
            "-setboost" -> statCue(fields.getOrNull(4)?.toIntOrNull(), BattleAudioCue.STAT_BOOST, BattleAudioCue.STAT_DROP)
            else -> null
        }
    }

    fun cueForNativeValue(value: String): BattleAudioCue? = when (value) {
        "generic_damage" -> BattleAudioCue.GENERIC_DAMAGE
        "super_effective" -> BattleAudioCue.SUPER_EFFECTIVE
        "not_very_effective" -> BattleAudioCue.NOT_VERY_EFFECTIVE
        "stat_boost" -> BattleAudioCue.STAT_BOOST
        "stat_drop" -> BattleAudioCue.STAT_DROP
        else -> null
    }

    private fun statCue(amount: Int?, positiveCue: BattleAudioCue, negativeCue: BattleAudioCue): BattleAudioCue? = when {
        amount == null || amount == 0 -> null
        amount > 0 -> positiveCue
        else -> negativeCue
    }
}
