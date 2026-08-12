package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleDisplayRefreshSchedulerTest {
    @Test
    fun coalescesRefreshRequestsUntilScheduledWorkRuns() {
        val scheduled = mutableListOf<Runnable>()
        var refreshes = 0
        val scheduler = BattleDisplayRefreshScheduler({ scheduled.add(it) }) { refreshes += 1 }

        scheduler.request()
        scheduler.request()
        scheduler.request()

        assertEquals(1, scheduled.size)
        assertEquals(0, refreshes)

        scheduled.single().run()

        assertEquals(1, refreshes)
    }

    @Test
    fun allowsAnotherRequestAfterTheScheduledRefreshRuns() {
        val scheduled = mutableListOf<Runnable>()
        var refreshes = 0
        val scheduler = BattleDisplayRefreshScheduler({ scheduled.add(it) }) { refreshes += 1 }

        scheduler.request()
        scheduled.single().run()
        scheduler.request()
        scheduled[1].run()

        assertEquals(2, refreshes)
    }

    @Test
    fun cancelAllowsAnAlreadyQueuedRunnableToBecomeHarmless() {
        val scheduled = mutableListOf<Runnable>()
        var refreshes = 0
        val scheduler = BattleDisplayRefreshScheduler({ scheduled.add(it) }) { refreshes += 1 }

        scheduler.request()
        scheduler.cancel()
        scheduled.single().run()

        assertEquals(0, refreshes)
    }

    @Test
    fun cancelAllowsAReplacementRequestAfterAQueuedRunnableIsRemoved() {
        val scheduled = mutableListOf<Runnable>()
        var refreshes = 0
        val scheduler = BattleDisplayRefreshScheduler({ scheduled.add(it) }) { refreshes += 1 }

        scheduler.request()
        scheduler.cancel()
        scheduler.request()
        scheduled[1].run()

        assertEquals(1, refreshes)
    }
}
