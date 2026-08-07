package com.showdown.ds

object MovePresentationTiming {
    fun duration(visualDurationMillis: Long) = visualDurationMillis.coerceAtLeast(0L)
}
