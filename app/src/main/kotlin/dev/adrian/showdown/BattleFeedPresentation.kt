package dev.adrian.showdown

import java.util.ArrayDeque

data class BattleFeedFrame(
    val text: String,
    val alpha: Float
)

class BattleFeedPresentation(
    private val minimumMessageDurationMillis: Long = 1_200L,
    private val visibleDurationMillis: Long = 3_600L,
    private val fadeDurationMillis: Long = 260L
) {
    private val pendingMessages = ArrayDeque<String>()
    private var observedEntries: List<String>? = null
    private var currentText: String? = null
    private var currentStartedAtMillis = 0L

    fun update(entries: List<String>, visible: Boolean, nowMillis: Long) {
        if (!visible || entries.isEmpty()) {
            pendingMessages.clear()
            currentText = null
            observedEntries = entries
            return
        }
        val previousEntries = observedEntries
        if (previousEntries == null) {
            currentText = entries.last()
            currentStartedAtMillis = nowMillis
        } else if (previousEntries.isEmpty()) {
            pendingMessages.clear()
            currentText = entries.last()
            currentStartedAtMillis = nowMillis
        } else {
            newEntries(previousEntries, entries).forEach { message ->
                if (message.isNotBlank()) pendingMessages.addLast(message)
            }
            while (pendingMessages.size > MAX_PENDING_MESSAGES) pendingMessages.removeFirst()
            advance(nowMillis)
        }
        observedEntries = entries
    }

    fun frame(nowMillis: Long): BattleFeedFrame? {
        advance(nowMillis)
        val text = currentText ?: return null
        val ageMillis = (nowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        val fadeStartMillis = visibleDurationMillis
        val endMillis = fadeStartMillis + fadeDurationMillis
        if (ageMillis >= endMillis && pendingMessages.isEmpty()) {
            currentText = null
            return null
        }
        val alpha = when {
            ageMillis < fadeDurationMillis -> ageMillis.toFloat() / fadeDurationMillis
            ageMillis < fadeStartMillis || pendingMessages.isNotEmpty() -> 1f
            else -> 1f - (ageMillis - fadeStartMillis).toFloat() / fadeDurationMillis
        }
        return BattleFeedFrame(text, alpha.coerceIn(0f, 1f))
    }

    fun needsAnimation(nowMillis: Long): Boolean {
        val text = currentText ?: return pendingMessages.isNotEmpty()
        val ageMillis = (nowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        return pendingMessages.isNotEmpty() || (text.isNotBlank() && ageMillis < visibleDurationMillis + fadeDurationMillis)
    }

    private fun advance(nowMillis: Long) {
        val current = currentText
        if (current == null) {
            if (pendingMessages.isNotEmpty()) {
                currentText = pendingMessages.removeFirst()
                currentStartedAtMillis = nowMillis
            }
            return
        }
        val ageMillis = (nowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        if (pendingMessages.isNotEmpty() && ageMillis >= minimumMessageDurationMillis) {
            currentText = pendingMessages.removeFirst()
            currentStartedAtMillis = nowMillis
        }
    }

    private fun newEntries(previous: List<String>, current: List<String>): List<String> {
        if (current.size >= previous.size && current.take(previous.size) == previous) {
            return current.drop(previous.size)
        }
        if (current == previous) return emptyList()
        return current.lastOrNull()?.let(::listOf).orEmpty()
    }

    private companion object {
        const val MAX_PENDING_MESSAGES = 8
    }
}
