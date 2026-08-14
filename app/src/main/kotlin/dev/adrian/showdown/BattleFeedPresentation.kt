package dev.adrian.showdown

import java.util.ArrayDeque

data class BattleFeedFrame(
    val text: String,
    val alpha: Float,
    val visibleText: String
)

class BattleFeedPresentation(
    private val charactersPerSecond: Float = 32f,
    private val minimumMessageDurationMillis: Long = 1_500L,
    private val holdDurationMillis: Long = 650L,
    private val fadeDurationMillis: Long = 240L
) {
    private val pendingMessages = ArrayDeque<String>()
    private var observedEntries: List<String>? = null
    private var currentText: String? = null
    private var currentStartedAtMillis = 0L
    private var playbackSpeed = 1f
    private var feedVisible = true
    private var playbackPaused = false
    private var playbackPausedAtMillis = 0L
    private var accumulatedPausedMillis = 0L

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed = value.coerceIn(0.25f, 4f)
    }

    fun setPlaybackPaused(value: Boolean, nowMillis: Long) {
        if (playbackPaused == value) return
        if (value) {
            playbackPaused = true
            playbackPausedAtMillis = nowMillis
        } else {
            accumulatedPausedMillis += (nowMillis - playbackPausedAtMillis).coerceAtLeast(0L)
            playbackPausedAtMillis = 0L
            playbackPaused = false
        }
    }

    fun reset() {
        pendingMessages.clear()
        observedEntries = null
        currentText = null
        currentStartedAtMillis = 0L
        feedVisible = true
        playbackPaused = false
        playbackPausedAtMillis = 0L
        accumulatedPausedMillis = 0L
    }

    fun advanceOnTap(nowMillis: Long) {
        if (playbackPaused || !feedVisible) return
        val presentationNowMillis = presentationNowMillis(nowMillis)
        val text = currentText
        if (text == null) {
            if (pendingMessages.isNotEmpty()) {
                currentText = pendingMessages.removeFirst()
                currentStartedAtMillis = presentationNowMillis
            }
            return
        }
        val ageMillis = (presentationNowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        val revealDuration = revealDurationMillis(text)
        if (ageMillis < revealDuration) {
            currentStartedAtMillis = presentationNowMillis - revealDuration
        } else if (pendingMessages.isNotEmpty()) {
            currentText = pendingMessages.removeFirst()
            currentStartedAtMillis = presentationNowMillis
        } else {
            currentText = null
        }
    }

    fun update(entries: List<String>, visible: Boolean, nowMillis: Long) {
        val presentationNowMillis = presentationNowMillis(nowMillis)
        feedVisible = visible
        if (entries.isEmpty()) {
            pendingMessages.clear()
            currentText = null
            observedEntries = entries
            return
        }
        val previousEntries = observedEntries
        if (previousEntries == null) {
            pendingMessages.clear()
            currentText = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else if (previousEntries.isEmpty()) {
            pendingMessages.clear()
            currentText = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else if (isNewBattle(previousEntries, entries)) {
            pendingMessages.clear()
            currentText = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else {
            newEntries(previousEntries, entries).forEach { message ->
                enqueue(message)
            }
            if (!playbackPaused) advance(presentationNowMillis)
        }
        observedEntries = entries
    }

    fun frame(nowMillis: Long): BattleFeedFrame? {
        val presentationNowMillis = presentationNowMillis(nowMillis)
        if (!playbackPaused) advance(presentationNowMillis)
        val text = currentText ?: return null
        val ageMillis = (presentationNowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        val fadeStartMillis = messageVisibleDurationMillis(text)
        val fadeDuration = scaledFadeDurationMillis()
        val endMillis = fadeStartMillis + fadeDuration
        if (ageMillis >= endMillis) {
            if (!feedVisible || pendingMessages.isEmpty()) {
                currentText = null
                return null
            }
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
        if (playbackPaused) return false
        val text = currentText ?: return pendingMessages.isNotEmpty()
        val ageMillis = (presentationNowMillis(nowMillis) - currentStartedAtMillis).coerceAtLeast(0L)
        return pendingMessages.isNotEmpty() || (text.isNotBlank() && ageMillis < messageVisibleDurationMillis(text) + scaledFadeDurationMillis())
    }

    private fun advance(nowMillis: Long) {
        if (!feedVisible) return
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

    private fun enqueue(message: String) {
        if (message.isBlank()) return
        pendingMessages.addLast(message)
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

    private fun presentationNowMillis(nowMillis: Long): Long {
        val pausedMillis = if (playbackPaused) {
            (nowMillis - playbackPausedAtMillis).coerceAtLeast(0L)
        } else {
            0L
        }
        return (nowMillis - accumulatedPausedMillis - pausedMillis).coerceAtLeast(0L)
    }

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
        val overlap = (minOf(previous.size, current.size) downTo 1)
            .firstOrNull { size -> previous.takeLast(size) == current.take(size) }
            ?: 0
        return current.drop(overlap)
    }

    private fun isNewBattle(previous: List<String>, current: List<String>): Boolean {
        if (current.size < previous.size) return true
        return current.size == 1 && current.firstOrNull() != previous.firstOrNull()
    }

    private companion object {
        const val MINIMUM_REVEAL_DURATION_MILLIS = 320L
        const val MAXIMUM_REVEAL_DURATION_MILLIS = 1_400L
    }
}
