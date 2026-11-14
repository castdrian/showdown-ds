package dev.adrian.showdown

import android.app.Activity
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import java.util.concurrent.CountDownLatch
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class ShowdownLogParityHarnessActivity : Activity() {
    val nativeEntries = CopyOnWriteArrayList<Pair<Long, String>>()
    val nativeProtocolEventIds = CopyOnWriteArrayList<Long?>()
    val nativeProtocolMessageIds = CopyOnWriteArrayList<List<Long>>()
    val synchronizedGenerations = CopyOnWriteArrayList<Long>()
    val javascriptErrors = CopyOnWriteArrayList<String>()
    private val synchronizationMonitor = java.lang.Object()
    lateinit var renderer: ShowdownMoveEffectsView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        renderer = ShowdownMoveEffectsView(
            context = this,
            audioCueListener = {},
            battleLogListener = { value, generation, protocolEventId, protocolMessageIds ->
                nativeEntries += generation to value
                nativeProtocolEventIds += protocolEventId
                nativeProtocolMessageIds += protocolMessageIds
            },
            battleLogSyncListener = { generation ->
                synchronizedGenerations += generation
                synchronized(synchronizationMonitor) {
                    synchronizationMonitor.notifyAll()
                }
            }
        )
        renderer.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                if (message.messageLevel() == ConsoleMessage.MessageLevel.ERROR) {
                    javascriptErrors += "${message.sourceId()}:${message.lineNumber()} ${message.message()}"
                }
                return true
            }
        }
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

    fun nativePlaybackDiagnostics(timeoutMillis: Long): String {
        val result = AtomicReference("unavailable")
        val latch = CountDownLatch(1)
        runOnUiThread {
            renderer.evaluateJavascript("window.ShowdownNativeEffects.diagnostics()") {
                result.set(it.orEmpty())
                latch.countDown()
            }
        }
        if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) return "unavailable"
        return result.get()
    }

    override fun onDestroy() {
        renderer.release()
        super.onDestroy()
    }
}
