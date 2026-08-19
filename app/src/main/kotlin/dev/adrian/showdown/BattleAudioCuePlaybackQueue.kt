package dev.adrian.showdown

import kotlin.math.roundToLong

data class BattleAudioCuePlayback(
    val cue: BattleAudioCue,
    val delayMillis: Long
)

class BattleAudioCuePlaybackQueue {
    private var nextStandardAvailableAtMillis = 0L
    private var lastImpactAtMillis = Long.MIN_VALUE
    private var playbackSpeed = 1f

    @Synchronized
    fun reset(nowMillis: Long = 0L) {
        nextStandardAvailableAtMillis = nowMillis
        lastImpactAtMillis = Long.MIN_VALUE
    }

    @Synchronized
    fun setPlaybackSpeed(value: Float) {
        playbackSpeed = value.coerceIn(0.5f, 2f)
    }

    @Synchronized
    fun enqueue(cue: BattleAudioCue, requestedAtMillis: Long): BattleAudioCuePlayback {
        val startAtMillis = when (cue) {
            BattleAudioCue.GENERIC_DAMAGE -> requestedAtMillis
            BattleAudioCue.SUPER_EFFECTIVE,
            BattleAudioCue.NOT_VERY_EFFECTIVE -> maxOf(
                requestedAtMillis,
                lastImpactAtMillis + playbackDurationMillis(BattleAudioCue.GENERIC_DAMAGE) + CUE_GAP_MILLIS
            )
            BattleAudioCue.STAT_BOOST,
            BattleAudioCue.STAT_DROP -> requestedAtMillis
        }
        if (cue == BattleAudioCue.GENERIC_DAMAGE) lastImpactAtMillis = startAtMillis
        nextStandardAvailableAtMillis = maxOf(
            nextStandardAvailableAtMillis,
            startAtMillis + playbackDurationMillis(cue) + CUE_GAP_MILLIS
        )
        return BattleAudioCuePlayback(cue, startAtMillis - requestedAtMillis)
    }

    @Synchronized
    fun availableAtMillis(): Long = nextStandardAvailableAtMillis

    @Synchronized
    fun playbackDurationMillis(cue: BattleAudioCue): Long =
        (cue.playbackDurationMillis / playbackSpeed).roundToLong().coerceAtLeast(1L)

    private companion object {
        const val CUE_GAP_MILLIS = 24L
    }
}
