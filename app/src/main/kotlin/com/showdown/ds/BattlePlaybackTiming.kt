package com.showdown.ds

object BattlePlaybackTiming {
    const val EVENT_PAUSE_MILLIS = 700L

    fun chunks(lines: List<String>): List<List<String>> {
        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        lines.forEach { line ->
            if (current.isNotEmpty() && isActionBoundary(line)) {
                chunks += current
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    fun pauseAfter(lines: List<String>): Long = when {
        lines.any { it.startsWith("|win|") || it.startsWith("|tie|") } -> END_OF_BATTLE_PAUSE_MILLIS
        lines.any { it.startsWith("|faint|") } -> FAINT_PAUSE_MILLIS
        lines.any { it.startsWith("|move|") } -> MOVE_PAUSE_MILLIS
        lines.any { it.startsWith("|switch|") || it.startsWith("|drag|") || it.startsWith("|replace|") } -> SWITCH_PAUSE_MILLIS
        lines.any { it.startsWith("|turn|") } -> TURN_PAUSE_MILLIS
        else -> 0L
    }

    private fun isActionBoundary(line: String) =
        line.startsWith("|move|") ||
            line.startsWith("|switch|") ||
            line.startsWith("|drag|") ||
            line.startsWith("|replace|") ||
            line.startsWith("|turn|") ||
            line.startsWith("|request|") ||
            line.startsWith("|win|") ||
            line.startsWith("|tie|")

    private const val MOVE_PAUSE_MILLIS = EVENT_PAUSE_MILLIS
    private const val FAINT_PAUSE_MILLIS = 950L
    private const val SWITCH_PAUSE_MILLIS = 850L
    private const val TURN_PAUSE_MILLIS = 500L
    private const val END_OF_BATTLE_PAUSE_MILLIS = 1_400L
}
