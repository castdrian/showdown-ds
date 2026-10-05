package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlaybackBarrierTest {
    @Test
    fun waitsForReadableDwellAndAnimationCompletion() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 42L)

        assertFalse(barrier.minimumDwellElapsed())
        assertFalse(barrier.effectsCompleted(token = 41L))
        assertTrue(barrier.effectsCompleted(token = 42L))
    }

    @Test
    fun waitsForReadableDwellWhenAnimationsFinishFirst() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 8L)

        assertFalse(barrier.effectsCompleted(token = 8L))
        assertTrue(barrier.minimumDwellElapsed())
    }

    @Test
    fun waitsForRendererCompletionAfterReadableDwell() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 3L)

        assertFalse(barrier.minimumDwellElapsed())
        assertFalse(barrier.minimumDwellElapsed())
        assertTrue(barrier.effectsCompleted(token = 3L))
    }

    @Test
    fun resetDiscardsAnOutstandingAnimationWait() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 15L)
        barrier.reset()

        assertTrue(barrier.minimumDwellElapsed())
        assertFalse(barrier.effectsCompleted(token = 15L))
    }

    @Test
    fun staleAnimationCallbacksCannotReleaseANewerPacket() {
        val barrier = BattlePlaybackBarrier()
        barrier.begin(token = 20L)
        barrier.minimumDwellElapsed()
        barrier.begin(token = 21L)

        assertFalse(barrier.effectsCompleted(token = 20L))
        assertFalse(barrier.minimumDwellElapsed())
        assertTrue(barrier.effectsCompleted(token = 21L))
    }
}
