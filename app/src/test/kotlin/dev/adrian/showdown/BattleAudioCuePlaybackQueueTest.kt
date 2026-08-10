package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleAudioCuePlaybackQueueTest {
    @Test
    fun schedulesTheNextCueAfterThePreviousCueFinishes() {
        val queue = BattleAudioCuePlaybackQueue()

        assertEquals(0L, queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L).delayMillis)
        assertEquals(
            BattleAudioCue.GENERIC_DAMAGE.playbackDurationMillis,
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
                1_000L + BattleAudioCue.GENERIC_DAMAGE.playbackDurationMillis
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
}
