package dev.adrian.showdown

data class BattleAudioCuePlayback(
    val cue: BattleAudioCue,
    val delayMillis: Long
)

class BattleAudioCuePlaybackQueue {
    private var nextStandardAvailableAtMillis = 0L
    private var lastImpactAtMillis = Long.MIN_VALUE

    @Synchronized
    fun reset(nowMillis: Long = 0L) {
        nextStandardAvailableAtMillis = nowMillis
        lastImpactAtMillis = Long.MIN_VALUE
    }

    @Synchronized
    fun enqueue(cue: BattleAudioCue, requestedAtMillis: Long): BattleAudioCuePlayback {
        val startAtMillis = when (cue) {
            BattleAudioCue.GENERIC_DAMAGE -> requestedAtMillis
            BattleAudioCue.SUPER_EFFECTIVE,
            BattleAudioCue.NOT_VERY_EFFECTIVE -> maxOf(
                requestedAtMillis,
                lastImpactAtMillis + IMPACT_RESULT_DELAY_MILLIS
            )
            else -> maxOf(requestedAtMillis, nextStandardAvailableAtMillis)
        }
        if (cue == BattleAudioCue.GENERIC_DAMAGE) lastImpactAtMillis = startAtMillis
        nextStandardAvailableAtMillis = maxOf(
            nextStandardAvailableAtMillis,
            startAtMillis + cue.playbackDurationMillis + CUE_GAP_MILLIS
        )
        return BattleAudioCuePlayback(cue, startAtMillis - requestedAtMillis)
    }

    @Synchronized
    fun availableAtMillis(): Long = nextStandardAvailableAtMillis

    private companion object {
        const val CUE_GAP_MILLIS = 24L
        const val IMPACT_RESULT_DELAY_MILLIS = 180L
    }
}
