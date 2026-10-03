package dev.adrian.showdown

import kotlin.math.roundToLong

object BattlePlaybackTiming {
    const val EVENT_PAUSE_MILLIS = 2_600L

    fun chunks(lines: List<String>): List<List<String>> {
        val chunks = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        lines.forEach { line ->
            if (current.isNotEmpty() && (isActionBoundary(line) || line == "|")) {
                chunks += current
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) chunks += current
        return chunks
    }

    fun pauseAfter(
        lines: List<String>,
        generatedMessageCount: Int = 0,
        feedPlaybackBudgetMillis: Long = 0L
    ): Long {
        val actionPause = when {
            lines.any {
                it.startsWith("|win|") ||
                    it.startsWith("|tie|") ||
                    it.startsWith("|draw|") ||
                    it.startsWith("|prematureend|")
            } -> END_OF_BATTLE_PAUSE_MILLIS
            lines.any { it.startsWith("|faint|") } -> FAINT_PAUSE_MILLIS
            lines.any { it.startsWith("|move|") } -> MOVE_PAUSE_MILLIS
            lines.any { it.startsWith("|-anim|") } -> MOVE_PAUSE_MILLIS
            lines.any { it.startsWith("|switch|") || it.startsWith("|drag|") || it.startsWith("|replace|") } -> SWITCH_PAUSE_MILLIS
            lines.any { it.startsWith("|turn|") } -> TURN_PAUSE_MILLIS
            else -> 0L
        }
        val messageCount = maxOf(readableMessageCount(lines), generatedMessageCount.coerceAtLeast(0).toLong())
        return maxOf(actionPause, messageCount * MESSAGE_PAUSE_MILLIS, feedPlaybackBudgetMillis.coerceAtLeast(0L))
    }

    fun isDecisionChunk(lines: List<String>): Boolean = lines.any { it.startsWith("|request|") }

    fun scaledPause(pauseMillis: Long, speed: Float): Long {
        if (pauseMillis <= 0L) return 0L
        return (pauseMillis / BattlePlaybackSpeed.coerce(speed)).roundToLong().coerceAtLeast(1L)
    }

    private fun readableMessageCount(lines: List<String>): Long = lines.count { line ->
        val action = line.split('|').getOrNull(1).orEmpty()
        val directMessage = !line.startsWith('|') || line.startsWith("||")
        line.isNotBlank() &&
            (directMessage || !line.contains("|[silent]") && (action.startsWith("-") || action in READABLE_ACTIONS))
    }.toLong()

    private fun isActionBoundary(line: String) =
        line.startsWith("|move|") ||
            line.startsWith("|cant|") ||
            line.startsWith("|switch|") ||
            line.startsWith("|switchout|") ||
            line.startsWith("|drag|") ||
            line.startsWith("|replace|") ||
            line == "|start" ||
            line.startsWith("|faint|") ||
            line.startsWith("|-damage|") && line.contains("|[from] confusion", ignoreCase = true) ||
            line.startsWith("|-curestatus|") && line.contains("|[from] ability: Natural Cure", ignoreCase = true) ||
            line.startsWith("|-start|") && line.contains("|[from] ability: Protean", ignoreCase = true) ||
            isPreMajorActivation(line) ||
            line.startsWith("|detailschange|") ||
            line.startsWith("|swap|") ||
            line.startsWith("|-formechange|") ||
            line.startsWith("|-transform|") ||
            line.startsWith("|-burst|") ||
            line.startsWith("|-mega|") ||
            line.startsWith("|-candynamax|") ||
            line.startsWith("|-primal|") ||
            line.startsWith("|-terastallize|") ||
            line == "|upkeep" ||
            line.startsWith("|turn|") ||
            line.startsWith("|request|") ||
            line.startsWith("|win|") ||
            line.startsWith("|tie|") ||
            line.startsWith("|draw|") ||
            line.startsWith("|prematureend|")

    private fun isPreMajorActivation(line: String): Boolean {
        if (!line.startsWith("|-activate|")) return false
        val effect = line.split('|').getOrNull(3).orEmpty()
        val effectId = effect.substringAfterLast(':').filter(Char::isLetterOrDigit).lowercase()
        return effectId in PRE_MAJOR_ACTIVATIONS
    }

    private const val MOVE_PAUSE_MILLIS = EVENT_PAUSE_MILLIS
    private const val FAINT_PAUSE_MILLIS = 3_200L
    private const val SWITCH_PAUSE_MILLIS = 2_800L
    private const val TURN_PAUSE_MILLIS = 2_000L
    private const val END_OF_BATTLE_PAUSE_MILLIS = 4_000L
    private const val MESSAGE_PAUSE_MILLIS = BattleFeedPresentation.DEFAULT_MESSAGE_CYCLE_MILLIS
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
        "start",
        "switch",
        "switchout",
        "tie",
        "win"
    )
    private val PRE_MAJOR_ACTIVATIONS = setOf("confusion", "attract", "pursuit")
}
