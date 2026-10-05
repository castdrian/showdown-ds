package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlaybackBarrierTest {
    @Test
    fun waitsForReadableDwellAndAnimationCompletion() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 42L, nowMillis = 0L)

        assertFalse(barrier.minimumDwellElapsed(nowMillis = 2_400L))
        assertFalse(barrier.effectsCompleted(token = 41L))
        assertTrue(barrier.effectsCompleted(token = 42L))
    }

    @Test
    fun waitsForReadableDwellWhenAnimationsFinishFirst() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 8L, nowMillis = 100L)

        assertFalse(barrier.effectsCompleted(token = 8L))
        assertTrue(barrier.minimumDwellElapsed(nowMillis = 2_500L))
    }

    @Test
    fun releasesAStalledRendererAfterItsTimeout() {
        val barrier = BattlePlaybackBarrier(effectsTimeoutMillis = 8_000L)
        barrier.begin(token = 3L, nowMillis = 1_000L)

        assertFalse(barrier.minimumDwellElapsed(nowMillis = 2_000L))
        assertTrue(barrier.minimumDwellElapsed(nowMillis = 9_000L))
    }

    @Test
    fun resetDiscardsAnOutstandingAnimationWait() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 15L, nowMillis = 0L)
        barrier.reset()

        assertTrue(barrier.minimumDwellElapsed(nowMillis = 100L))
        assertFalse(barrier.effectsCompleted(token = 15L))
    }

    @Test
    fun staleAnimationCallbacksCannotReleaseANewerPacket() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 20L, nowMillis = 0L)
        barrier.minimumDwellElapsed(nowMillis = 2_000L)
        barrier.begin(token = 21L, nowMillis = 2_100L)

        assertFalse(barrier.effectsCompleted(token = 20L))
        assertFalse(barrier.minimumDwellElapsed(nowMillis = 4_500L))
        assertTrue(barrier.effectsCompleted(token = 21L))
    }

    @Test
    fun pausedPlaybackDoesNotConsumeTheRendererTimeout() {
        val barrier = BattlePlaybackBarrier(effectsTimeoutMillis = 8_000L)
        barrier.begin(token = 30L, nowMillis = 1_000L)
        barrier.pause(nowMillis = 1_500L)
        barrier.resume(nowMillis = 20_000L)

        assertFalse(barrier.minimumDwellElapsed(nowMillis = 27_499L))
        assertTrue(barrier.minimumDwellElapsed(nowMillis = 27_501L))
    }
}
