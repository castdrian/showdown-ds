package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleAudioCuePlaybackQueueTest {
    @Test
    fun startsTheNextCueAfterThePreviousSoundFinishes() {
        val queue = BattleAudioCuePlaybackQueue()

        assertEquals(0L, queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L).delayMillis)
        assertEquals(
            BattleAudioCue.GENERIC_DAMAGE.playbackDurationMillis + 24L,
            queue.enqueue(BattleAudioCue.SUPER_EFFECTIVE, 1_000L).delayMillis
        )
    }

    @Test
    fun startsLaterRequestsWhenTheQueueIsFree() {
        val queue = BattleAudioCuePlaybackQueue()
        queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L)

        assertEquals(
            0L,
            queue.enqueue(
                BattleAudioCue.STAT_BOOST,
                1_724L
            ).delayMillis
        )
    }

    @Test
    fun resetMakesTheNextCueStartImmediately() {
        val queue = BattleAudioCuePlaybackQueue()
        queue.enqueue(BattleAudioCue.STAT_DROP, 1_000L)
        queue.reset(2_000L)

        assertEquals(0L, queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 2_000L).delayMillis)
    }

    @Test
    fun exposesTheEndOfTheScheduledCueTimeline() {
        val queue = BattleAudioCuePlaybackQueue()
        queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L)

        assertEquals(1_724L, queue.availableAtMillis())
    }
}
