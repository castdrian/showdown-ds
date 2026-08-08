package com.showdown.ds

object MovePresentationTiming {
    fun duration(visualDurationMillis: Long) = visualDurationMillis.takeIf { it > 0L } ?: MINIMUM_DURATION_MILLIS

    private const val MINIMUM_DURATION_MILLIS = 850L
}
