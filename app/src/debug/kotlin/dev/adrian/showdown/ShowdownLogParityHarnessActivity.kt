package dev.adrian.showdown

import android.app.Activity
import android.os.Bundle
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit

class ShowdownLogParityHarnessActivity : Activity() {
    val nativeEntries = CopyOnWriteArrayList<Pair<Long, String>>()
    val synchronizedGenerations = CopyOnWriteArrayList<Long>()
    private val synchronizationMonitor = java.lang.Object()
    lateinit var renderer: ShowdownMoveEffectsView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = ShowdownMoveEffectsView(
            context = this,
            audioCueListener = {},
            battleLogListener = { value, generation -> nativeEntries += generation to value },
            battleLogSyncListener = { generation ->
                synchronizedGenerations += generation
                synchronized(synchronizationMonitor) {
                    synchronizationMonitor.notifyAll()
                }
            }
        )
        setContentView(renderer)
        renderer.setPerspective("p1")
        renderer.setPlaybackSpeed(BattlePlaybackSpeed.MAXIMUM)
        renderer.setAnimationsDisabledForTesting(true)
    }

    fun awaitBattleLogSynchronization(previousCount: Int, generation: Long, timeoutMillis: Long): Boolean {
        val deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)
        synchronized(synchronizationMonitor) {
            while (synchronizedGenerations.drop(previousCount).none { it == generation }) {
                val remainingNanos = deadlineNanos - System.nanoTime()
                if (remainingNanos <= 0L) return false
                synchronizationMonitor.wait(TimeUnit.NANOSECONDS.toMillis(remainingNanos).coerceAtLeast(1L))
            }
        }
        return true
    }

    override fun onDestroy() {
        renderer.release()
        super.onDestroy()
    }
}
