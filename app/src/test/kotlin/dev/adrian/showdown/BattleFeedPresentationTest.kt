package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class BattleFeedPresentationTest {
    private fun fastPresentation() = BattleFeedPresentation(
        minimumMessageDurationMillis = 1_500L,
        holdDurationMillis = 650L,
        fadeDurationMillis = 240L
    )

    @Test
    fun startsWithTheLatestMessageWithoutReplayingHistory() {
        val presentation = fastPresentation()

        presentation.update(listOf("Older", "Newest"), true, 1_000L)

        assertEquals("Newest", presentation.frame(1_100L)?.text)
        assertNull(presentation.frame(2_800L))
    }

    @Test
    fun fadesAnUnchangedMessageAway() {
        val presentation = fastPresentation()
        presentation.update(listOf("Move text"), true, 1_000L)

        assertEquals(1f, presentation.frame(1_500L)?.alpha)
        assertEquals(0.13f, presentation.frame(2_685L)?.alpha ?: 0f, 0.01f)
        assertNull(presentation.frame(2_750L))
    }

    @Test
    fun keepsTheBattleMessageReadableAtTheDefaultBattleSpeed() {
        val presentation = BattleFeedPresentation()
        presentation.setPlaybackSpeed(0.75f)
        presentation.update(listOf("Meowstic used Nasty Plot!"), true, 1_000L)

        assertEquals("Meowstic used Nasty Plot!", presentation.frame(3_600L)?.visibleText)
        assertNull(presentation.frame(4_300L))
    }

    @Test
    fun advancesEachBattleMessageWithinThePacketPlaybackBudget() {
        val presentation = BattleFeedPresentation()
        presentation.setPlaybackSpeed(0.75f)
        presentation.update(listOf("Move"), true, 1_000L)
        presentation.update(listOf("Move", "Damage"), true, 1_001L)

        val nextMessageAt = 1_001L + BattlePlaybackTiming.scaledPause(
            BattlePlaybackTiming.pauseAfter(listOf("|-damage|p2a: Eevee|80/100")),
            0.75f
        )

        assertEquals("Damage", presentation.frame(nextMessageAt)?.text)
    }

    @Test
    fun showsTheCompleteMessageDuringItsFadeAndHold() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("Hello"), true, 1_000L)

        assertEquals("Hello", presentation.frame(1_100L)?.visibleText)
        assertEquals("Hello", presentation.frame(1_500L)?.visibleText)
        assertEquals(0.5f, presentation.frame(1_550L)?.alpha ?: 0f, 0.01f)
    }

    @Test
    fun selectedPlaybackSpeedChangesTheFadeRate() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 0L,
            fadeDurationMillis = 100L
        )
        presentation.setPlaybackSpeed(0.5f)
        presentation.update(listOf("Hello"), true, 1_000L)

        assertEquals("Hello", presentation.frame(1_100L)?.visibleText)
        assertEquals(1f, presentation.frame(1_200L)?.alpha)
    }

    @Test
    fun fadesIntoTheNextMessageAfterTheReadableCycle() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)

        assertEquals("First", presentation.frame(1_499L)?.visibleText)
        assertEquals("Second", presentation.frame(1_600L)?.text)
        assertEquals("Second", presentation.frame(1_700L)?.visibleText)
    }

    @Test
    fun replacesTheMessageWhenAnewEntryArrives() {
        val presentation = fastPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)

        assertEquals("First", presentation.frame(1_100L)?.text)
        assertEquals("Second", presentation.frame(2_800L)?.text)
    }

    @Test
    fun fullyFadedMessageDoesNotResurfaceAfterHiddenBoundary() {
        val presentation = fastPresentation()
        presentation.update(listOf("Old"), true, 1_000L)
        assertNull(presentation.frame(2_800L))
        presentation.update(listOf("Old"), false, 2_000L)
        presentation.update(listOf("Old"), true, 3_000L)

        assertNull(presentation.frame(3_100L))
    }

    @Test
    fun hiddenBoundaryKeepsPendingMessagesInReadableOrder() {
        val presentation = fastPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)
        presentation.update(listOf("First", "Second"), false, 1_200L)
        presentation.update(listOf("First", "Second", "Third"), true, 1_300L)

        assertEquals("First", presentation.frame(1_300L)?.text)
        assertEquals("Second", presentation.frame(2_800L)?.text)
    }

    @Test
    fun transcriptReconciliationDoesNotDiscardQueuedMessages() {
        val presentation = fastPresentation()
        presentation.update(listOf("First", "Second"), true, 1_000L)
        presentation.update(listOf("First", "Native detail", "Second"), true, 1_100L)

        assertEquals("Second", presentation.frame(1_100L)?.text)
        assertEquals("Native detail", presentation.frame(2_800L)?.text)
    }

    @Test
    fun nativeWordingReplacementDoesNotCreateAnotherReadableEvent() {
        val presentation = fastPresentation()
        presentation.update(listOf("First", "Pikachu recovered health."), true, 1_000L)
        presentation.update(listOf("First", "Pikachu restored health!"), true, 1_100L)

        assertEquals("Pikachu restored health!", presentation.frame(1_100L)?.text)
        assertNull(presentation.frame(2_800L))
    }

    @Test
    fun separatorLetsTheCurrentMessageFinishItsReadableCycle() {
        val presentation = fastPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First"), false, 1_100L)

        assertEquals("First", presentation.frame(1_100L)?.text)
    }

    @Test
    fun separatorWaitsBeforeStartingTheQueuedMessage() {
        val presentation = fastPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), false, 1_100L)

        assertEquals("First", presentation.frame(1_300L)?.text)
        assertNull(presentation.frame(2_800L))
        presentation.update(listOf("First", "Second"), true, 2_900L)

        assertEquals("Second", presentation.frame(2_900L)?.text)
    }

    @Test
    fun playbackBudgetDrainsTheCurrentAndQueuedMessagesBeforeAdvancingBattleState() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 1_000L,
            holdDurationMillis = 1_000L,
            fadeDurationMillis = 250L
        )
        val first = BattleFeedMessage(1L, "First")
        val second = BattleFeedMessage(2L, "Second")
        val third = BattleFeedMessage(3L, "Third")

        presentation.updateMessages(listOf(first), true, 1_000L)
        presentation.updateMessages(listOf(first, second, third), true, 1_300L)

        assertEquals(3_450L, presentation.remainingPlaybackBudgetMillis(1_300L))
        presentation.updateMessages(listOf(first, second, third), false, 1_400L)
        assertEquals(0L, presentation.remainingPlaybackBudgetMillis(1_400L))
    }

    @Test
    fun aNewBattleReplacesThePreviousMessageImmediately() {
        val presentation = fastPresentation()
        presentation.update(listOf("Old battle"), true, 1_000L)
        presentation.update(listOf("New battle"), true, 1_100L)

        assertEquals("New battle", presentation.frame(1_100L)?.text)
    }

    @Test
    fun explicitResetAllowsAnIdenticalOpeningMessageToAppearAgain() {
        val presentation = fastPresentation()
        presentation.update(listOf("Battle started."), true, 1_000L)
        presentation.reset()
        presentation.update(listOf("Battle started."), true, 2_000L)

        assertEquals("Battle started.", presentation.frame(2_000L)?.text)
    }

    @Test
    fun skipsSnapshotHistoryButQueuesLiveMessagesInOrder() {
        val presentation = fastPresentation()
        presentation.update(listOf("Old 1", "Old 2", "Old 3"), true, 1_000L)
        presentation.update(listOf("Old 1", "Old 2", "Old 3", "New 1", "New 2"), true, 1_100L)

        assertEquals("Old 3", presentation.frame(1_100L)?.text)
        assertEquals("New 1", presentation.frame(2_800L)?.text)
        assertEquals("New 2", presentation.frame(4_600L)?.text)
    }

    @Test
    fun keepsTheLatestLineWhenAHistoryWindowAdvances() {
        val presentation = fastPresentation()
        presentation.update(listOf("One", "Two", "Three"), true, 1_000L)
        presentation.update(listOf("Two", "Three", "Four"), true, 1_100L)

        assertEquals("Three", presentation.frame(1_100L)?.text)
        assertEquals("Four", presentation.frame(2_800L)?.text)
    }

    @Test
    fun queuesNewMessagesWhenTheRollingFeedWindowShrinks() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 0L,
            fadeDurationMillis = 100L
        )
        val previous = (38L..43L).map { BattleFeedMessage(it, "Old $it") }
        val current = listOf(
            BattleFeedMessage(42L, "Old 42"),
            BattleFeedMessage(43L, "Old 43"),
            BattleFeedMessage(70L, "Raging Bolt used Dragon Pulse!"),
            BattleFeedMessage(71L, "Cresselia lost 37% of its health!"),
            BattleFeedMessage(72L, "Cresselia lost Eject Button.")
        )

        presentation.updateMessages(previous, true, 1_000L)
        presentation.updateMessages(current, true, 1_100L)

        assertEquals(43L, presentation.frame(1_100L)?.messageId)
        assertEquals(70L, presentation.frame(1_200L)?.messageId)
        assertEquals("Raging Bolt used Dragon Pulse!", presentation.frame(1_200L)?.text)
        assertEquals(71L, presentation.frame(1_400L)?.messageId)
        assertEquals(72L, presentation.frame(1_600L)?.messageId)
    }

    @Test
    fun shorterFeedWithoutAnOverlapReplacesThePreviousSnapshot() {
        val presentation = fastPresentation()
        val previous = listOf(
            BattleFeedMessage(1L, "Old 1"),
            BattleFeedMessage(2L, "Old 2"),
            BattleFeedMessage(3L, "Old 3")
        )
        val current = listOf(
            BattleFeedMessage(4L, "New 1"),
            BattleFeedMessage(5L, "New 2")
        )

        presentation.updateMessages(previous, true, 1_000L)
        presentation.updateMessages(current, true, 1_100L)

        assertEquals(5L, presentation.frame(1_100L)?.messageId)
    }

    @Test
    fun skipsAReplacedHistorySnapshotWithoutASharedBoundary() {
        val presentation = fastPresentation()
        presentation.update(listOf("Protocol status", "Format"), true, 1_000L)
        presentation.update(
            listOf("Go! Pikachu!", "Pikachu used Tackle!", "It was super effective."),
            true,
            1_100L
        )

        assertEquals("It was super effective.", presentation.frame(1_100L)?.text)
        assertNull(presentation.frame(2_900L))
    }

    @Test
    fun pausesTheMessageClockWhileTheActivityIsPaused() {
        val presentation = fastPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)
        val beforePause = presentation.frame(1_200L)

        presentation.setPlaybackPaused(true, 1_200L)
        assertEquals(beforePause, presentation.frame(4_000L))

        presentation.setPlaybackPaused(false, 4_000L)
        assertEquals("First", presentation.frame(4_100L)?.text)
        assertEquals("First", presentation.frame(4_100L)?.visibleText)
    }

    @Test
    fun keepsEventsArrivingWhilePausedInOrderAfterResume() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)
        presentation.setPlaybackPaused(true, 1_100L)
        presentation.update(listOf("First", "Second", "Third"), true, 2_000L)
        presentation.setPlaybackPaused(false, 4_000L)

        assertEquals("Second", presentation.frame(5_000L)?.text)
        assertEquals("Third", presentation.frame(6_200L)?.text)
    }

    @Test
    fun serializesEveryMessageInARecentBurst() {
        val presentation = fastPresentation()
        presentation.update(listOf("Current"), true, 1_000L)
        presentation.update(listOf("Current") + (1..8).map { "Event $it" }, true, 1_100L)

        assertEquals("Event 1", presentation.frame(2_900L)?.text)
        assertEquals("Event 2", presentation.frame(4_800L)?.text)
    }

    @Test
    fun tapCompletesTheCurrentFadeBeforeMovingOn() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)

        presentation.advanceOnTap(1_050L)

        assertEquals("First", presentation.frame(1_050L)?.visibleText)
        assertEquals("First", presentation.frame(1_500L)?.visibleText)
    }

    @Test
    fun secondTapAdvancesToTheNextQueuedMessage() {
        val presentation = BattleFeedPresentation(
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)

        presentation.advanceOnTap(1_050L)
        presentation.advanceOnTap(1_100L)

        assertEquals("Second", presentation.frame(1_100L)?.text)
        assertEquals("Second", presentation.frame(1_100L)?.visibleText)
    }

    @Test
    fun resetStartsReadableFeedClockAfterPause() {
        val presentation = fastPresentation()
        presentation.setPlaybackPaused(true, 1_000L)
        presentation.reset()
        presentation.update(listOf("New battle"), true, 5_000L)

        assertEquals(0f, presentation.frame(5_000L)?.alpha)
    }

    @Test
    fun keepsTheTerminalBattleResultVisibleUntilTheNextBattle() {
        val presentation = fastPresentation()

        presentation.update(
            listOf("Pikachu used Thunderbolt!", "ADRIAN won the battle."),
            true,
            1_000L,
            "ADRIAN won the battle."
        )

        assertEquals("Pikachu used Thunderbolt!", presentation.frame(1_100L)?.text)
        assertEquals("ADRIAN won the battle.", presentation.frame(2_800L)?.text)
        assertEquals("ADRIAN won the battle.", presentation.frame(30_000L)?.text)
        assertEquals(1f, presentation.frame(30_000L)?.alpha)
        assertFalse(presentation.needsAnimation(30_000L))

        presentation.reset()
        presentation.update(listOf("Battle started."), true, 31_000L)

        assertEquals("Battle started.", presentation.frame(31_000L)?.text)
    }
}
