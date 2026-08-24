package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleAudioCuePlaybackQueueTest {
    @Test
    fun staleQueuedCuesUseTheFlushTimeAsTheirPlaybackOrigin() {
        assertEquals(5_000L, effectiveBattleAudioCueRequestTime(1_000L, 5_000L))
        assertEquals(6_000L, effectiveBattleAudioCueRequestTime(6_000L, 5_000L))
    }

    @Test
    fun effectivenessStartsAfterTheImpactSampleFinishes() {
        val queue = BattleAudioCuePlaybackQueue()

        assertEquals(0L, queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L).delayMillis)
        assertEquals(
            724L,
            queue.enqueue(BattleAudioCue.SUPER_EFFECTIVE, 1_000L).delayMillis
        )
    }

    @Test
    fun effectivenessKeepsItsOffsetFromAnEarlierDamageEvent() {
        val queue = BattleAudioCuePlaybackQueue()

        queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L)

        assertEquals(
            624L,
            queue.enqueue(BattleAudioCue.SUPER_EFFECTIVE, 1_100L).delayMillis
        )
    }

    @Test
    fun impactAudioIsNotBlockedByAnEarlierStatusCue() {
        val queue = BattleAudioCuePlaybackQueue()

        queue.enqueue(BattleAudioCue.STAT_BOOST, 1_000L)

        assertEquals(0L, queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L).delayMillis)
    }

    @Test
    fun statCuesStartAtTheirBattleEventEvenWhileImpactAudioIsPlaying() {
        val queue = BattleAudioCuePlaybackQueue()

        queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L)

        assertEquals(
            0L,
            queue.enqueue(BattleAudioCue.STAT_BOOST, 1_100L).delayMillis
        )
        assertEquals(
            0L,
            queue.enqueue(BattleAudioCue.STAT_DROP, 1_200L).delayMillis
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

    @Test
    fun scalesTheEffectivenessGapWithBattlePlaybackSpeed() {
        val queue = BattleAudioCuePlaybackQueue()
        queue.setPlaybackSpeed(0.5f)

        queue.enqueue(BattleAudioCue.GENERIC_DAMAGE, 1_000L)

        assertEquals(1_424L, queue.enqueue(BattleAudioCue.SUPER_EFFECTIVE, 1_000L).delayMillis)
    }

    @Test
    fun clampsSoundPoolPlaybackToItsSupportedRateRange() {
        val queue = BattleAudioCuePlaybackQueue()
        queue.setPlaybackSpeed(4f)

        assertEquals(350L, queue.playbackDurationMillis(BattleAudioCue.GENERIC_DAMAGE))
    }
}
