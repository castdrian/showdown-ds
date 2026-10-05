package dev.adrian.showdown

class BattlePlaybackBarrier {
    private var awaitedToken: Long? = null
    private var minimumDwellPassed = false

    fun begin(token: Long) {
        awaitedToken = token
        minimumDwellPassed = false
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

    fun reset() {
        awaitedToken = null
        minimumDwellPassed = false
    }
    private fun releaseIfReady(): Boolean {
        if (!minimumDwellPassed || awaitedToken != null) return false
        reset()
        return true
    }
}
