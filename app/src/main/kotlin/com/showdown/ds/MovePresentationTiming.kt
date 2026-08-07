package com.showdown.ds

import kotlin.math.ceil

object MovePresentationTiming {
    data class Plan(
        val animationCycles: Int,
        val durationMillis: Long
    )

    fun plan(visualDurationMillis: Long, audioDurationMillis: Long): Plan {
        if (visualDurationMillis <= 0L) return Plan(1, 0L)
        val cycles = if (audioDurationMillis <= 0L) {
            1
        } else {
            ceil(audioDurationMillis.toDouble() / visualDurationMillis).toInt().coerceAtLeast(1)
        }
        return Plan(cycles, visualDurationMillis * cycles)
    }
}
