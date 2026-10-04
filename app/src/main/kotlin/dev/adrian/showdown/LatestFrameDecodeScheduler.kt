package dev.adrian.showdown

import java.util.concurrent.Executor
import java.util.concurrent.RejectedExecutionException

internal class LatestFrameDecodeScheduler(
    private val executor: Executor,
    private val decodeFrame: (Int) -> Unit
) {
    private val lock = Any()
    private var requestedFrameIndex: Int? = null
    private var inFlightFrameIndex: Int? = null
    private var workerScheduled = false

    fun request(frameIndex: Int) {
        val scheduleWorker = synchronized(lock) {
            if (workerScheduled) {
                requestedFrameIndex = frameIndex.takeUnless { it == inFlightFrameIndex }
                false
            } else {
                requestedFrameIndex = frameIndex
                workerScheduled = true
                true
            }
        }
        if (!scheduleWorker) return
        try {
            executor.execute(::decodeRequestedFrames)
        } catch (_: RejectedExecutionException) {
            synchronized(lock) { workerScheduled = false }
        }
    }

    private fun decodeRequestedFrames() {
        while (true) {
            val frameIndex = synchronized(lock) {
                requestedFrameIndex?.also {
                    requestedFrameIndex = null
                    inFlightFrameIndex = it
                } ?: run {
                    workerScheduled = false
                    inFlightFrameIndex = null
                    return
                }
            }
            try {
                decodeFrame(frameIndex)
            } catch (error: Throwable) {
                synchronized(lock) {
                    workerScheduled = false
                    inFlightFrameIndex = null
                }
                throw error
            }
            synchronized(lock) { inFlightFrameIndex = null }
        }
    }
}
