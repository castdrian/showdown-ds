package dev.adrian.showdown

data class BattleAudioCuePlayback(
    val cue: BattleAudioCue,
    val delayMillis: Long
)

class BattleAudioCuePlaybackQueue {
    private var nextAvailableAtMillis = 0L

    @Synchronized
    fun reset(nowMillis: Long = 0L) {
        nextAvailableAtMillis = nowMillis
    }

    @Synchronized
    fun enqueue(cue: BattleAudioCue, requestedAtMillis: Long): BattleAudioCuePlayback {
        val startAtMillis = maxOf(requestedAtMillis, nextAvailableAtMillis)
        nextAvailableAtMillis = startAtMillis + cue.playbackDurationMillis
        return BattleAudioCuePlayback(cue, startAtMillis - requestedAtMillis)
    }
}
