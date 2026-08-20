package dev.adrian.showdown

import kotlin.math.roundToLong

data class BattleAnnouncerCuePlayback(
    val cue: BattleAnnouncerCue,
    val delayMillis: Long
)

class BattleAnnouncerCuePlaybackQueue {
    private var nextAvailableAtMillis = 0L
    private var playbackSpeed = 1f

    @Synchronized
    fun reset(nowMillis: Long = 0L) {
        nextAvailableAtMillis = nowMillis
    }

    @Synchronized
    fun setPlaybackSpeed(value: Float) {
        playbackSpeed = BattlePlaybackSpeed.coerce(value)
    }

    @Synchronized
    fun enqueue(cue: BattleAnnouncerCue, requestedAtMillis: Long): BattleAnnouncerCuePlayback {
        val startAtMillis = maxOf(requestedAtMillis, nextAvailableAtMillis)
        nextAvailableAtMillis = startAtMillis + playbackDurationMillis(cue) + CUE_GAP_MILLIS
        return BattleAnnouncerCuePlayback(cue, startAtMillis - requestedAtMillis)
    }

    @Synchronized
    fun playbackDurationMillis(cue: BattleAnnouncerCue): Long =
        (cue.playbackDurationMillis / playbackSpeed).roundToLong().coerceAtLeast(1L)

    private companion object {
        const val CUE_GAP_MILLIS = 24L
    }
}
