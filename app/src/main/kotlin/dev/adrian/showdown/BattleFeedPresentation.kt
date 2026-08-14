package dev.adrian.showdown

import java.util.ArrayDeque

data class BattleFeedFrame(
    val text: String,
    val alpha: Float,
    val visibleText: String
)

class BattleFeedPresentation(
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
        val fadeDuration = scaledFadeDurationMillis()
        if (ageMillis < fadeDuration) {
            currentStartedAtMillis = presentationNowMillis - fadeDuration
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
        reconcileMessageWording(entries)
        if (previousEntries == null) {
            pendingMessages.clear()
            currentText = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else if (previousEntries.isEmpty()) {
            pendingMessages.clear()
            currentText = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else if (isContinuation(previousEntries, entries)) {
            newEntries(previousEntries, entries).forEach { message ->
                enqueue(message)
            }
            if (!playbackPaused) advance(presentationNowMillis)
        } else if (isSnapshotReplacement(previousEntries, entries)) {
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

    private fun reconcileMessageWording(entries: List<String>) {
        currentText = currentText?.let { current ->
            entries.firstOrNull { entry -> BattleFeedMessageIdentity.matches(current, entry) } ?: current
        }
        if (pendingMessages.isEmpty()) return
        val queued = pendingMessages.toList()
        pendingMessages.clear()
        queued.forEach { message ->
            pendingMessages.addLast(
                entries.firstOrNull { entry -> BattleFeedMessageIdentity.matches(message, entry) } ?: message
            )
        }
    }

    fun frame(nowMillis: Long): BattleFeedFrame? {
        val presentationNowMillis = presentationNowMillis(nowMillis)
        if (!playbackPaused) advance(presentationNowMillis)
        val text = currentText ?: return null
        val ageMillis = (presentationNowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        val fadeStartMillis = messageVisibleDurationMillis()
        val fadeDuration = scaledFadeDurationMillis()
        val endMillis = fadeStartMillis + fadeDuration
        if (ageMillis >= endMillis) {
            if (!feedVisible || pendingMessages.isEmpty()) {
                currentText = null
                return null
            }
        }
        val alpha = when {
            ageMillis < fadeDuration -> easedProgress(ageMillis.toFloat() / fadeDuration)
            ageMillis < fadeStartMillis -> 1f
            else -> 1f - easedProgress((ageMillis - fadeStartMillis).toFloat() / fadeDuration)
        }
        return BattleFeedFrame(
            text = text,
            alpha = alpha.coerceIn(0f, 1f),
            visibleText = text
        )
    }

    fun needsAnimation(nowMillis: Long): Boolean {
        if (playbackPaused) return false
        val text = currentText ?: return pendingMessages.isNotEmpty()
        val ageMillis = (presentationNowMillis(nowMillis) - currentStartedAtMillis).coerceAtLeast(0L)
        return pendingMessages.isNotEmpty() || (text.isNotBlank() && ageMillis < messageVisibleDurationMillis() + scaledFadeDurationMillis())
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
        if (pendingMessages.isNotEmpty() && ageMillis >= messageVisibleDurationMillis() + scaledFadeDurationMillis()) {
            currentText = pendingMessages.removeFirst()
            currentStartedAtMillis = nowMillis
        }
    }

    private fun enqueue(message: String) {
        if (message.isBlank()) return
        pendingMessages.addLast(message)
    }

    private fun messageVisibleDurationMillis(): Long = maxOf(
        scaledMinimumMessageDurationMillis(),
        scaledHoldDurationMillis(),
        scaledFadeDurationMillis()
    )

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

    private fun easedProgress(value: Float): Float {
        val progress = value.coerceIn(0f, 1f)
        return progress * progress * (3f - 2f * progress)
    }

    private fun newEntries(previous: List<String>, current: List<String>): List<String> {
        if (isContinuation(previous, current)) {
            val additions = mutableListOf<String>()
            var previousIndex = 0
            current.forEach { entry ->
                if (previousIndex < previous.size && BattleFeedMessageIdentity.matches(previous[previousIndex], entry)) {
                    previousIndex += 1
                } else {
                    additions += entry
                }
            }
            return additions
        }
        if (current.size >= previous.size && current.take(previous.size) == previous) {
            return current.drop(previous.size)
        }
        if (sameSequence(previous, current)) return emptyList()
        val overlap = (minOf(previous.size, current.size) downTo 1)
            .firstOrNull { size -> sameSequence(previous.takeLast(size), current.take(size)) }
            ?: 0
        return current.drop(overlap)
    }

    private fun isSnapshotReplacement(previous: List<String>, current: List<String>): Boolean {
        if (sameSequence(previous, current)) return false
        if (isContinuation(previous, current)) return false
        if (current.size < previous.size) return true
        if (current.size == 1 && !BattleFeedMessageIdentity.matches(current.firstOrNull().orEmpty(), previous.firstOrNull().orEmpty())) return true
        if (previous.isEmpty() || current.isEmpty()) return false
        return (minOf(previous.size, current.size) downTo 1).none { size ->
            sameSequence(previous.takeLast(size), current.take(size))
        }
    }

    private fun isContinuation(previous: List<String>, current: List<String>): Boolean {
        if (previous.isEmpty() || current.size < previous.size) return false
        var previousIndex = 0
        current.forEach { entry ->
            if (previousIndex < previous.size && BattleFeedMessageIdentity.matches(previous[previousIndex], entry)) previousIndex += 1
        }
        return previousIndex == previous.size
    }

    private fun sameSequence(first: List<String>, second: List<String>): Boolean =
        first.size == second.size && first.indices.all { index ->
            BattleFeedMessageIdentity.matches(first[index], second[index])
        }
}
