package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlaybackBarrierTest {
    @Test
    fun waitsForReadableDwellAndAnimationCompletion() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 42L, nowMillis = 0L)

        assertFalse(barrier.minimumDwellElapsed())
        assertFalse(barrier.effectsCompleted(token = 41L))
        assertTrue(barrier.effectsCompleted(token = 42L))
    }

    @Test
    fun waitsForReadableDwellWhenAnimationsFinishFirst() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 8L, nowMillis = 100L)

        assertFalse(barrier.effectsCompleted(token = 8L))
        assertTrue(barrier.minimumDwellElapsed())
    }

    @Test
    fun waitsForRendererCompletionAfterReadableDwell() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 3L, nowMillis = 0L)

        assertFalse(barrier.minimumDwellElapsed())
        assertFalse(barrier.minimumDwellElapsed())
        assertTrue(barrier.effectsCompleted(token = 3L))
    }

    @Test
    fun rendererTimeoutRequestsTwoRecoveryAttemptsBeforeFailingClosed() {
        val barrier = BattlePlaybackBarrier(rendererTimeoutMillis = 8_000L)
        barrier.begin(token = 24L, nowMillis = 1_000L)

        assertFalse(barrier.recoveryDue(nowMillis = 8_999L))
        assertEquals(1_000L, barrier.millisUntilRecovery(nowMillis = 8_000L))
        assertTrue(barrier.recoveryDue(nowMillis = 9_000L))
        assertEquals(1, barrier.recoveryAttempts())
        assertFalse(barrier.recoveryDue(nowMillis = 16_999L))
        assertTrue(barrier.recoveryDue(nowMillis = 17_000L))
        assertEquals(2, barrier.recoveryAttempts())
        assertFalse(barrier.recoveryTimedOut(nowMillis = 24_999L))
        assertTrue(barrier.recoveryTimedOut(nowMillis = 25_000L))
    }

    @Test
    fun pausedPlaybackDoesNotConsumeRendererRecoveryTime() {
        val barrier = BattlePlaybackBarrier(rendererTimeoutMillis = 8_000L)
        barrier.begin(token = 30L, nowMillis = 1_000L)
        barrier.pause(nowMillis = 1_000L)

        assertFalse(barrier.recoveryDue(nowMillis = 20_000L))

        barrier.resume(nowMillis = 20_000L)

        assertFalse(barrier.recoveryDue(nowMillis = 27_999L))
        assertTrue(barrier.recoveryDue(nowMillis = 28_000L))
    }

    @Test
    fun resetDiscardsAnOutstandingAnimationWait() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 15L, nowMillis = 0L)
        barrier.reset()

        assertTrue(barrier.minimumDwellElapsed())
        assertFalse(barrier.effectsCompleted(token = 15L))
    }

    @Test
    fun staleAnimationCallbacksCannotReleaseANewerPacket() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 20L, nowMillis = 0L)
        barrier.minimumDwellElapsed()
        barrier.begin(token = 21L, nowMillis = 2_100L)

        assertFalse(barrier.effectsCompleted(token = 20L))
        assertFalse(barrier.minimumDwellElapsed())
        assertTrue(barrier.effectsCompleted(token = 21L))
    }
}
