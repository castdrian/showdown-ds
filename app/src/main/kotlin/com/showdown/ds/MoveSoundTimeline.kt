package com.showdown.ds

class MoveSoundTimeline {
    private var currentToken = 0L
    private var playbackDeadlineMillis = Long.MIN_VALUE

    fun beginMove(nowMillis: Long = 0L): Long {
        currentToken += 1
        playbackDeadlineMillis = nowMillis + PLAYBACK_WINDOW_MILLIS
        return currentToken
    }

    fun isCurrent(token: Long) = token == currentToken

    fun isPlayable(token: Long, nowMillis: Long) = isCurrent(token) && nowMillis <= playbackDeadlineMillis

    private companion object {
        const val PLAYBACK_WINDOW_MILLIS = 750L
    }
}
