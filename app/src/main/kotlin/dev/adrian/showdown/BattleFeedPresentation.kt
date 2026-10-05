package dev.adrian.showdown

import java.util.ArrayDeque
import kotlin.math.ceil

data class BattleFeedFrame(
    val text: String,
    val alpha: Float,
    val visibleText: String,
    val messageId: Long? = null
)

data class BattleFeedMessage(
    val id: Long,
    val text: String
)

object BattleFeedSceneState {
    fun hasKnownPokemon(
        combatant: BattleSession.ActiveCombatant?,
        details: BattleSession.PokemonDetails,
        requireActiveCombatant: Boolean = false
    ): Boolean {
        if (requireActiveCombatant && combatant == null) return false
        val species = combatant?.let { it.species.ifBlank { it.name } }
            ?: details.species.ifBlank { details.name }
        return isUsableSpriteSpecies(species)
    }

    fun combatantsForMessage(
        currentCombatants: List<BattleSession.ActiveCombatant>,
        playerSide: Boolean,
        switchOutVisual: BattleSession.SwitchOutVisual?
    ): List<BattleSession.ActiveCombatant> {
        val visual = switchOutVisual ?: return currentCombatants
        if (visual.playerSide != playerSide) return currentCombatants
        var replaced = false
        val visibleCombatants = currentCombatants.map { combatant ->
            if (combatant.slot == visual.combatant.slot) {
                replaced = true
                visual.combatant
            } else {
                combatant
            }
        }
        return if (replaced) visibleCombatants else currentCombatants
    }

    fun detailsForMessage(
        currentDetails: BattleSession.PokemonDetails,
        playerSide: Boolean,
        switchOutVisual: BattleSession.SwitchOutVisual?
    ): BattleSession.PokemonDetails {
        val visual = switchOutVisual ?: return currentDetails
        return if (visual.playerSide == playerSide) visual.details else currentDetails
    }
}

class BattleFeedPresentation(
    private val minimumMessageDurationMillis: Long = DEFAULT_MESSAGE_DWELL_MILLIS,
    private val holdDurationMillis: Long = DEFAULT_MESSAGE_DWELL_MILLIS,
    private val fadeDurationMillis: Long = DEFAULT_MESSAGE_FADE_MILLIS
) {
    private val pendingMessages = ArrayDeque<BattleFeedMessage>()
    private var observedEntries: List<BattleFeedMessage>? = null
    private var legacyObservedEntries: List<BattleFeedMessage>? = null
    private var nextLegacyMessageId = -1L
    private var currentMessage: BattleFeedMessage? = null
    private var currentStartedAtMillis = 0L
    private var playbackSpeed = 1f
    private var feedVisible = true
    private var playbackPaused = false
    private var playbackPausedAtMillis = 0L
    private var accumulatedPausedMillis = 0L
    private var persistentText: String? = null
    private var pendingPersistentText: String? = null

    companion object {
        const val DEFAULT_MESSAGE_DWELL_MILLIS = 2_000L
        const val DEFAULT_MESSAGE_FADE_MILLIS = 400L
        const val DEFAULT_MESSAGE_CYCLE_MILLIS = DEFAULT_MESSAGE_DWELL_MILLIS + DEFAULT_MESSAGE_FADE_MILLIS
    }

    fun setPlaybackSpeed(value: Float) {
        playbackSpeed = BattlePlaybackSpeed.coerce(value)
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
        clearMessageState()
        feedVisible = true
        playbackPaused = false
        playbackPausedAtMillis = 0L
        accumulatedPausedMillis = 0L
    }

    fun advanceOnTap(nowMillis: Long) {
        if (persistentText != null) return
        if (playbackPaused || !feedVisible) return
        val presentationNowMillis = presentationNowMillis(nowMillis)
        if (currentMessage == null) {
            if (pendingMessages.isNotEmpty()) {
                currentMessage = pendingMessages.removeFirst()
                currentStartedAtMillis = presentationNowMillis
            }
            return
        }
        val ageMillis = (presentationNowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        val fadeDuration = scaledFadeDurationMillis()
        if (ageMillis < fadeDuration) {
            currentStartedAtMillis = presentationNowMillis - fadeDuration
        } else if (pendingMessages.isNotEmpty()) {
            currentMessage = pendingMessages.removeFirst()
            currentStartedAtMillis = presentationNowMillis
        } else {
            currentMessage = null
            promotePendingPersistentTextIfIdle()
        }
    }

    fun update(entries: List<String>, visible: Boolean, nowMillis: Long, persistentText: String? = null) {
        val previous = legacyObservedEntries.orEmpty().toMutableList()
        val messages = entries.map { text ->
            val matchIndex = previous.indexOfFirst { BattleFeedMessageIdentity.matches(it.text, text) }
            if (matchIndex < 0) {
                BattleFeedMessage(nextLegacyMessageId--, text)
            } else {
                previous.removeAt(matchIndex).copy(text = text)
            }
        }
        legacyObservedEntries = messages
        updateMessages(messages, visible, nowMillis, persistentText)
    }

    fun updateMessages(
        entries: List<BattleFeedMessage>,
        visible: Boolean,
        nowMillis: Long,
        persistentText: String? = null
    ) {
        val normalizedPersistentText = persistentText?.trim()?.takeIf(String::isNotBlank)
        if (observedEntries === entries && feedVisible == visible && this.persistentText == normalizedPersistentText) return
        val presentationNowMillis = presentationNowMillis(nowMillis)
        if (normalizedPersistentText != null) {
            if (this.persistentText == normalizedPersistentText) {
                observedEntries = entries
                return
            }
            pendingPersistentText = normalizedPersistentText
            updateEntries(
                entries.filterNot { BattleFeedMessageIdentity.matches(it.text, normalizedPersistentText) },
                visible,
                presentationNowMillis
            )
            promotePendingPersistentTextIfIdle()
            return
        }
        if (this.persistentText != null || pendingPersistentText != null) {
            clearMessageState()
        }
        updateEntries(entries, visible, presentationNowMillis)
    }

    private fun updateEntries(entries: List<BattleFeedMessage>, visible: Boolean, presentationNowMillis: Long) {
        feedVisible = visible
        if (entries.isEmpty()) {
            pendingMessages.clear()
            currentMessage = null
            observedEntries = entries
            promotePendingPersistentTextIfIdle()
            return
        }
        val previousEntries = observedEntries
        reconcileMessageWording(entries)
        if (previousEntries == null) {
            pendingMessages.clear()
            currentMessage = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else if (previousEntries.isEmpty()) {
            pendingMessages.clear()
            currentMessage = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else if (isContinuation(previousEntries, entries)) {
            newMessages(previousEntries, entries).forEach { message ->
                enqueue(message)
            }
            if (!playbackPaused) advance(presentationNowMillis)
        } else if (isSnapshotReplacement(previousEntries, entries)) {
            pendingMessages.clear()
            currentMessage = entries.last()
            currentStartedAtMillis = presentationNowMillis
        } else {
            newMessages(previousEntries, entries).forEach { message ->
                enqueue(message)
            }
            if (!playbackPaused) advance(presentationNowMillis)
        }
        observedEntries = entries
    }

    private fun reconcileMessageWording(entries: List<BattleFeedMessage>) {
        currentMessage = currentMessage?.let { current ->
            entries.firstOrNull { it.id == current.id }
                ?: entries.firstOrNull { entry -> BattleFeedMessageIdentity.matches(current.text, entry.text) }
                ?: current
        }
        if (pendingMessages.isEmpty()) return
        val queued = pendingMessages.toList()
        pendingMessages.clear()
        queued.forEach { message ->
            pendingMessages.addLast(
                entries.firstOrNull { it.id == message.id }
                    ?: entries.firstOrNull { entry -> BattleFeedMessageIdentity.matches(message.text, entry.text) }
                    ?: message
            )
        }
    }

    fun frame(nowMillis: Long): BattleFeedFrame? {
        val presentationNowMillis = presentationNowMillis(nowMillis)
        if (!playbackPaused) advance(presentationNowMillis)
        persistentText?.let { text ->
            return BattleFeedFrame(text = text, alpha = 1f, visibleText = text)
        }
        val message = currentMessage ?: return null
        val ageMillis = (presentationNowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        val fadeStartMillis = messageVisibleDurationMillis()
        val fadeDuration = scaledFadeDurationMillis()
        val endMillis = fadeStartMillis + fadeDuration
        if (ageMillis >= endMillis) {
            if (!feedVisible || pendingMessages.isEmpty()) {
                currentMessage = null
                return null
            }
        }
        val alpha = when {
            ageMillis < fadeDuration -> easedProgress(ageMillis.toFloat() / fadeDuration)
            ageMillis < fadeStartMillis -> 1f
            else -> 1f - easedProgress((ageMillis - fadeStartMillis).toFloat() / fadeDuration)
        }
        return BattleFeedFrame(
            text = message.text,
            alpha = alpha.coerceIn(0f, 1f),
            visibleText = message.text,
            messageId = message.id
        )
    }

    fun needsAnimation(nowMillis: Long): Boolean {
        if (persistentText != null) return false
        if (playbackPaused) return false
        val message = currentMessage ?: return pendingMessages.isNotEmpty() || pendingPersistentText != null
        val ageMillis = (presentationNowMillis(nowMillis) - currentStartedAtMillis).coerceAtLeast(0L)
        return pendingMessages.isNotEmpty() || (message.text.isNotBlank() && ageMillis < messageVisibleDurationMillis() + scaledFadeDurationMillis())
    }

    fun remainingPlaybackBudgetMillis(nowMillis: Long): Long {
        if (playbackPaused || !feedVisible) return 0L
        val presentationNowMillis = presentationNowMillis(nowMillis)
        advance(presentationNowMillis)
        val cycleMillis = messageVisibleDurationMillis() + scaledFadeDurationMillis()
        val currentRemainingMillis = currentMessage?.let {
            (cycleMillis - (presentationNowMillis - currentStartedAtMillis).coerceAtLeast(0L)).coerceAtLeast(0L)
        } ?: 0L
        val remainingMillis = currentRemainingMillis + pendingMessages.size * cycleMillis
        return ceil(remainingMillis * playbackSpeed).toLong()
    }

    private fun advance(nowMillis: Long) {
        if (!feedVisible) return
        val current = currentMessage
        if (current == null) {
            if (pendingMessages.isNotEmpty()) {
                currentMessage = pendingMessages.removeFirst()
                currentStartedAtMillis = nowMillis
            } else {
                promotePendingPersistentTextIfIdle()
            }
            return
        }
        val ageMillis = (nowMillis - currentStartedAtMillis).coerceAtLeast(0L)
        if (pendingMessages.isNotEmpty() && ageMillis >= messageVisibleDurationMillis() + scaledFadeDurationMillis()) {
            currentMessage = pendingMessages.removeFirst()
            currentStartedAtMillis = nowMillis
        } else if (pendingMessages.isEmpty() && ageMillis >= messageVisibleDurationMillis() + scaledFadeDurationMillis()) {
            currentMessage = null
            promotePendingPersistentTextIfIdle()
        }
    }

    private fun promotePendingPersistentTextIfIdle() {
        if (currentMessage != null || pendingMessages.isNotEmpty()) return
        pendingPersistentText?.let {
            persistentText = it
            pendingPersistentText = null
        }
    }

    private fun enqueue(message: BattleFeedMessage) {
        if (message.text.isBlank()) return
        pendingMessages.addLast(message)
    }

    private fun clearMessageState() {
        pendingMessages.clear()
        observedEntries = null
        legacyObservedEntries = null
        currentMessage = null
        currentStartedAtMillis = 0L
        persistentText = null
        pendingPersistentText = null
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

    private fun newMessages(previous: List<BattleFeedMessage>, current: List<BattleFeedMessage>): List<BattleFeedMessage> {
        if (isContinuation(previous, current)) {
            val additions = mutableListOf<BattleFeedMessage>()
            var previousIndex = 0
            current.forEach { entry ->
                if (previousIndex < previous.size && previous[previousIndex].id == entry.id) {
                    previousIndex += 1
                } else {
                    additions += entry
                }
            }
            return additions
        }
        if (current.size >= previous.size && current.take(previous.size).map { it.id } == previous.map { it.id }) {
            return current.drop(previous.size)
        }
        if (sameSequence(previous, current)) return emptyList()
        val overlap = (minOf(previous.size, current.size) downTo 1)
            .firstOrNull { size -> sameSequence(previous.takeLast(size), current.take(size)) }
            ?: 0
        return current.drop(overlap)
    }

    private fun isSnapshotReplacement(previous: List<BattleFeedMessage>, current: List<BattleFeedMessage>): Boolean {
        if (sameSequence(previous, current)) return false
        if (isContinuation(previous, current)) return false
        if (current.size == 1 && current.firstOrNull()?.id != previous.firstOrNull()?.id) return true
        if (previous.isEmpty() || current.isEmpty()) return false
        return (minOf(previous.size, current.size) downTo 1).none { size ->
            sameSequence(previous.takeLast(size), current.take(size))
        }
    }

    private fun isContinuation(previous: List<BattleFeedMessage>, current: List<BattleFeedMessage>): Boolean {
        if (previous.isEmpty() || current.size < previous.size) return false
        var previousIndex = 0
        current.forEach { entry ->
            if (previousIndex < previous.size && previous[previousIndex].id == entry.id) previousIndex += 1
        }
        return previousIndex == previous.size
    }

    private fun sameSequence(first: List<BattleFeedMessage>, second: List<BattleFeedMessage>): Boolean =
        first.size == second.size && first.indices.all { index -> first[index].id == second[index].id }
}
