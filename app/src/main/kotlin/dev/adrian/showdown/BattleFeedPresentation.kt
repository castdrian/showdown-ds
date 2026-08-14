package dev.adrian.showdown

import java.util.ArrayDeque

data class BattleFeedFrame(
    val text: String,
    val alpha: Float,
    val visibleText: String
)

class BattleFeedPresentation(
    private val charactersPerSecond: Float = 42f,
    private val minimumMessageDurationMillis: Long = 1_200L,
    private val holdDurationMillis: Long = 600L,
    private val fadeDurationMillis: Long = 220L
) {
    private val pendingMessages = ArrayDeque<String>()
    private var observedEntries: List<String>? = null
    private var currentText: String? = null
    private var currentStartedAtMillis = 0L
    private var playbackSpeed = 1f

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed = value.coerceIn(0.25f, 4f)
    }

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
        val fadeStartMillis = messageVisibleDurationMillis(text)
        val fadeDuration = scaledFadeDurationMillis()
        val endMillis = fadeStartMillis + fadeDuration
        if (ageMillis >= endMillis && pendingMessages.isEmpty()) {
            currentText = null
            return null
        }
        val revealedCharacters = ((ageMillis * charactersPerSecond * playbackSpeed) / 1_000f)
            .toInt()
            .coerceIn(0, text.codePointCount(0, text.length))
        val alpha = when {
            ageMillis < fadeDuration -> ageMillis.toFloat() / fadeDuration
            ageMillis < fadeStartMillis -> 1f
            else -> 1f - (ageMillis - fadeStartMillis).toFloat() / fadeDuration
        }
        return BattleFeedFrame(
            text = text,
            alpha = alpha.coerceIn(0f, 1f),
            visibleText = prefixByCodePoints(text, revealedCharacters)
        )
    }

    fun needsAnimation(nowMillis: Long): Boolean {
        val text = currentText ?: return pendingMessages.isNotEmpty()
        val ageMillis = (nowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        return pendingMessages.isNotEmpty() || (text.isNotBlank() && ageMillis < messageVisibleDurationMillis(text) + scaledFadeDurationMillis())
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
        if (pendingMessages.isNotEmpty() && ageMillis >= messageVisibleDurationMillis(current) + scaledFadeDurationMillis()) {
            currentText = pendingMessages.removeFirst()
            currentStartedAtMillis = nowMillis
        }
    }

    private fun messageVisibleDurationMillis(text: String): Long = maxOf(
        scaledMinimumMessageDurationMillis(),
        revealDurationMillis(text) + scaledHoldDurationMillis()
    )

    private fun revealDurationMillis(text: String): Long = (
        text.codePointCount(0, text.length) * 1_000f / (charactersPerSecond * playbackSpeed).coerceAtLeast(1f)
        ).toLong().coerceIn(MINIMUM_REVEAL_DURATION_MILLIS, MAXIMUM_REVEAL_DURATION_MILLIS)

    private fun scaledMinimumMessageDurationMillis() = (minimumMessageDurationMillis / playbackSpeed).toLong().coerceAtLeast(1L)

    private fun scaledHoldDurationMillis() = (holdDurationMillis / playbackSpeed).toLong().coerceAtLeast(1L)

    private fun scaledFadeDurationMillis() = (fadeDurationMillis / playbackSpeed).toLong().coerceAtLeast(1L)

    private fun prefixByCodePoints(value: String, count: Int): String {
        if (count <= 0) return ""
        var end = 0
        repeat(count.coerceAtMost(value.codePointCount(0, value.length))) {
            end += Character.charCount(value.codePointAt(end))
        }
        return value.substring(0, end)
    }

    private fun newEntries(previous: List<String>, current: List<String>): List<String> {
        if (current.size >= previous.size && current.take(previous.size) == previous) {
            return current.drop(previous.size)
        }
        if (current == previous) return emptyList()
        return current.lastOrNull()?.let(::listOf).orEmpty()
    }

    private companion object {
        const val MINIMUM_REVEAL_DURATION_MILLIS = 320L
        const val MAXIMUM_REVEAL_DURATION_MILLIS = 1_400L
        const val MAX_PENDING_MESSAGES = 8
    }
}
