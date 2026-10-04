package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executor

class LatestFrameDecodeSchedulerTest {
    @Test
    fun defersFrameWorkToExecutor() {
        val executor = QueuedExecutor()
        val decodedFrames = mutableListOf<Int>()
        val scheduler = LatestFrameDecodeScheduler(executor, decodedFrames::add)

        scheduler.request(3)

        assertTrue(decodedFrames.isEmpty())
        assertEquals(1, executor.pendingCount)
        executor.runNext()
        assertEquals(listOf(3), decodedFrames)
    }

    @Test
    fun coalescesRequestsMadeDuringDecodingToTheLatestFrame() {
        val executor = QueuedExecutor()
        val decodedFrames = mutableListOf<Int>()
        lateinit var scheduler: LatestFrameDecodeScheduler
        scheduler = LatestFrameDecodeScheduler(executor) { frame ->
            decodedFrames += frame
            if (frame == 2) {
                scheduler.request(4)
                scheduler.request(6)
            }
        }

        scheduler.request(2)
        executor.runNext()

        assertEquals(listOf(2, 6), decodedFrames)
        assertEquals(0, executor.pendingCount)
    }

    @Test
    fun ignoresRepeatedRequestsForTheFrameAlreadyDecoding() {
        val executor = QueuedExecutor()
        val decodedFrames = mutableListOf<Int>()
        lateinit var scheduler: LatestFrameDecodeScheduler
        scheduler = LatestFrameDecodeScheduler(executor) { frame ->
            decodedFrames += frame
            scheduler.request(frame)
        }

        scheduler.request(2)
        executor.runNext()

        assertEquals(listOf(2), decodedFrames)
        assertEquals(0, executor.pendingCount)
    }

    private class QueuedExecutor : Executor {
        private val tasks = mutableListOf<Runnable>()
        val pendingCount get() = tasks.size

        override fun execute(command: Runnable) {
            tasks += command
        }

        fun runNext() {
            tasks.removeAt(0).run()
        }
    }
}
