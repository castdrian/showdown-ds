package dev.adrian.showdown

import kotlin.math.roundToLong

object BattlePlaybackTiming {
    const val EVENT_PAUSE_MILLIS = 2_600L

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

    fun pauseAfter(lines: List<String>): Long {
        val actionPause = when {
            lines.any {
                it.startsWith("|win|") ||
                    it.startsWith("|tie|") ||
                    it.startsWith("|draw|") ||
                    it.startsWith("|prematureend|")
            } -> END_OF_BATTLE_PAUSE_MILLIS
            lines.any { it.startsWith("|faint|") } -> FAINT_PAUSE_MILLIS
            lines.any { it.startsWith("|move|") } -> MOVE_PAUSE_MILLIS
            lines.any { it.startsWith("|switch|") || it.startsWith("|drag|") || it.startsWith("|replace|") } -> SWITCH_PAUSE_MILLIS
            lines.any { it.startsWith("|turn|") } -> TURN_PAUSE_MILLIS
            else -> 0L
        }
        return maxOf(actionPause, readableMessageCount(lines) * MESSAGE_PAUSE_MILLIS)
    }

    fun isDecisionChunk(lines: List<String>): Boolean = lines.any { it.startsWith("|request|") }

    fun scaledPause(pauseMillis: Long, speed: Float): Long {
        if (pauseMillis <= 0L) return 0L
        return (pauseMillis / BattlePlaybackSpeed.coerce(speed)).roundToLong().coerceAtLeast(1L)
    }

    private fun readableMessageCount(lines: List<String>): Long = lines.count { line ->
        val action = line.split('|').getOrNull(1).orEmpty()
        !line.contains("|[silent]") &&
            (action.startsWith("-") || action in READABLE_ACTIONS)
    }.toLong()

    private fun isActionBoundary(line: String) =
        line.startsWith("|move|") ||
            line.startsWith("|switch|") ||
            line.startsWith("|drag|") ||
            line.startsWith("|replace|") ||
            line.startsWith("|turn|") ||
            line.startsWith("|request|") ||
            line.startsWith("|win|") ||
            line.startsWith("|tie|") ||
            line.startsWith("|draw|") ||
            line.startsWith("|prematureend|")

    private const val MOVE_PAUSE_MILLIS = EVENT_PAUSE_MILLIS
    private const val FAINT_PAUSE_MILLIS = 3_200L
    private const val SWITCH_PAUSE_MILLIS = 2_800L
    private const val TURN_PAUSE_MILLIS = 2_000L
    private const val END_OF_BATTLE_PAUSE_MILLIS = 4_000L
    private const val MESSAGE_PAUSE_MILLIS = 2_400L
    private val READABLE_ACTIONS = setOf(
        "cant",
        "custom",
        "draw",
        "drag",
        "faint",
        "hint",
        "message",
        "move",
        "prematureend",
        "replace",
        "switch",
        "tie",
        "win"
    )
}
