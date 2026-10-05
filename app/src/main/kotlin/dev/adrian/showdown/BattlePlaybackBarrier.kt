package dev.adrian.showdown

class BattlePlaybackBarrier(
    private val rendererTimeoutMillis: Long = DEFAULT_RENDERER_TIMEOUT_MILLIS,
    private val maximumRecoveryAttempts: Int = DEFAULT_MAXIMUM_RECOVERY_ATTEMPTS
) {
    private var awaitedToken: Long? = null
    private var minimumDwellPassed = false
    private var recoveryWindowStartedAtMillis: Long? = null
    private var pauseStartedAtMillis: Long? = null
    private var startedRecoveryAttempts = 0

    fun begin(token: Long, nowMillis: Long) {
        awaitedToken = token
        minimumDwellPassed = false
        recoveryWindowStartedAtMillis = nowMillis
        pauseStartedAtMillis = null
        startedRecoveryAttempts = 0
    }

    fun minimumDwellElapsed(): Boolean {
        minimumDwellPassed = true
        return releaseIfReady()
    }

    fun effectsCompleted(token: Long): Boolean {
        if (awaitedToken != token) return false
        awaitedToken = null
        return releaseIfReady()
    }

    fun recoveryDue(nowMillis: Long): Boolean {
        if (awaitedToken == null || pauseStartedAtMillis != null) return false
        val startedAt = recoveryWindowStartedAtMillis ?: return false
        if (nowMillis - startedAt < rendererTimeoutMillis || startedRecoveryAttempts >= maximumRecoveryAttempts) return false
        startedRecoveryAttempts += 1
        recoveryWindowStartedAtMillis = nowMillis
        return true
    }

    fun recoveryTimedOut(nowMillis: Long): Boolean {
        if (awaitedToken == null || pauseStartedAtMillis != null || startedRecoveryAttempts < maximumRecoveryAttempts) return false
        val startedAt = recoveryWindowStartedAtMillis ?: return false
        return nowMillis - startedAt >= rendererTimeoutMillis
    }

    fun millisUntilRecovery(nowMillis: Long): Long {
        if (pauseStartedAtMillis != null) return rendererTimeoutMillis
        val startedAt = recoveryWindowStartedAtMillis ?: return rendererTimeoutMillis
        return (rendererTimeoutMillis - (nowMillis - startedAt)).coerceAtLeast(0L)
    }

    fun recoveryAttempts(): Int = startedRecoveryAttempts

    fun pause(nowMillis: Long) {
        if (awaitedToken != null && pauseStartedAtMillis == null) pauseStartedAtMillis = nowMillis
    }

    fun resume(nowMillis: Long) {
        val pausedAt = pauseStartedAtMillis ?: return
        recoveryWindowStartedAtMillis = recoveryWindowStartedAtMillis?.plus((nowMillis - pausedAt).coerceAtLeast(0L))
        pauseStartedAtMillis = null
    }

    fun reset() {
        awaitedToken = null
        minimumDwellPassed = false
        recoveryWindowStartedAtMillis = null
        pauseStartedAtMillis = null
        startedRecoveryAttempts = 0
    }

    private fun releaseIfReady(): Boolean {
        if (!minimumDwellPassed || awaitedToken != null) return false
        reset()
        return true
    }

    private companion object {
        const val DEFAULT_RENDERER_TIMEOUT_MILLIS = 12_000L
        const val DEFAULT_MAXIMUM_RECOVERY_ATTEMPTS = 2
    }
}
