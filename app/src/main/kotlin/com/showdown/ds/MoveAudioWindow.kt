package com.showdown.ds

object MoveAudioWindow {
    data class Plan(val loop: Boolean, val durationMillis: Long)

    fun plan(audioDurationMillis: Long, visualDurationMillis: Long): Plan {
        val duration = visualDurationMillis.coerceAtLeast(0L)
        return Plan(loop = audioDurationMillis in 1 until duration, durationMillis = duration)
    }
}
