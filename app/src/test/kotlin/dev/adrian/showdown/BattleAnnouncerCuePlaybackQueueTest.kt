package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleAnnouncerCuePlaybackQueueTest {
    @Test
    fun cuesPlaySequentiallyWhenProtocolPacketsArriveTogether() {
        val queue = BattleAnnouncerCuePlaybackQueue()

        assertEquals(0L, queue.enqueue(BattleAnnouncerCue.MOVE, 1_000L).delayMillis)
        assertEquals(
            1_345L,
            queue.enqueue(BattleAnnouncerCue.HIT, 1_000L).delayMillis
        )
    }

    @Test
    fun playbackSpeedScalesTheAnnouncerDuration() {
        val queue = BattleAnnouncerCuePlaybackQueue()
        queue.setPlaybackSpeed(2f)

        assertTrue(queue.playbackDurationMillis(BattleAnnouncerCue.HEAL) < BattleAnnouncerCue.HEAL.playbackDurationMillis)
        assertEquals(1_044L, queue.playbackDurationMillis(BattleAnnouncerCue.HEAL))
    }

    @Test
    fun resetAllowsTheNextBattleToStartImmediately() {
        val queue = BattleAnnouncerCuePlaybackQueue()
        queue.enqueue(BattleAnnouncerCue.BATTLE_START, 1_000L)

        queue.reset(2_000L)

        assertEquals(0L, queue.enqueue(BattleAnnouncerCue.SWITCH, 2_000L).delayMillis)
    }
}
