package dev.adrian.showdown

class BattlePlaybackBarrier(
    private val effectsTimeoutMillis: Long = DEFAULT_EFFECTS_TIMEOUT_MILLIS
) {
    private var awaitedToken: Long? = null
    private var effectsStartedAtMillis = 0L
    private var pausedAtMillis: Long? = null
    private var minimumDwellPassed = false

    fun begin(token: Long, nowMillis: Long) {
        awaitedToken = token
        effectsStartedAtMillis = nowMillis
        pausedAtMillis = null
        minimumDwellPassed = false
    }

    fun minimumDwellElapsed(nowMillis: Long): Boolean {
        if (hasTimedOut(nowMillis)) awaitedToken = null
        minimumDwellPassed = true
        return releaseIfReady()
    }

    fun effectsCompleted(token: Long): Boolean {
        if (awaitedToken != token) return false
        awaitedToken = null
        return releaseIfReady()
    }

    fun pause(nowMillis: Long) {
        if (awaitedToken != null && pausedAtMillis == null) pausedAtMillis = nowMillis
    }

    fun resume(nowMillis: Long) {
        val pauseStartedAt = pausedAtMillis ?: return
        effectsStartedAtMillis += (nowMillis - pauseStartedAt).coerceAtLeast(0L)
        pausedAtMillis = null
    }

    fun reset() {
        awaitedToken = null
        effectsStartedAtMillis = 0L
        pausedAtMillis = null
        minimumDwellPassed = false
    }

    private fun hasTimedOut(nowMillis: Long): Boolean {
        val timeoutClock = pausedAtMillis ?: nowMillis
        return awaitedToken != null && timeoutClock - effectsStartedAtMillis >= effectsTimeoutMillis
    }

    private fun releaseIfReady(): Boolean {
        if (!minimumDwellPassed || awaitedToken != null) return false
        reset()
        return true
    }

    private companion object {
        const val DEFAULT_EFFECTS_TIMEOUT_MILLIS = 12_000L
    }
}
