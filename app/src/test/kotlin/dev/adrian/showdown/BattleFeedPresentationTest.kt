package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleFeedPresentationTest {
    @Test
    fun startsWithTheLatestMessageWithoutReplayingHistory() {
        val presentation = BattleFeedPresentation()

        presentation.update(listOf("Older", "Newest"), true, 1_000L)

        assertEquals("Newest", presentation.frame(1_100L)?.text)
        assertNull(presentation.frame(2_800L))
    }

    @Test
    fun fadesAnUnchangedMessageAway() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Move text"), true, 1_000L)

        assertEquals(1f, presentation.frame(1_500L)?.alpha)
        assertEquals(0.23f, presentation.frame(2_685L)?.alpha ?: 0f, 0.01f)
        assertNull(presentation.frame(2_750L))
    }

    @Test
    fun revealsTheMessageBeforeHoldingIt() {
        val presentation = BattleFeedPresentation(
            charactersPerSecond = 10f,
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("Hello"), true, 1_000L)

        assertEquals("H", presentation.frame(1_100L)?.visibleText)
        assertEquals("Hello", presentation.frame(1_500L)?.visibleText)
        assertEquals(1f, presentation.frame(1_900L)?.alpha)
    }

    @Test
    fun selectedPlaybackSpeedChangesTheRevealRate() {
        val presentation = BattleFeedPresentation(
            charactersPerSecond = 10f,
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 0L,
            fadeDurationMillis = 100L
        )
        presentation.setPlaybackSpeed(0.5f)
        presentation.update(listOf("Hello"), true, 1_000L)

        assertEquals("", presentation.frame(1_100L)?.visibleText)
        assertEquals("H", presentation.frame(1_200L)?.visibleText)
    }

    @Test
    fun fadesIntoTheNextMessageAfterTheReadableCycle() {
        val presentation = BattleFeedPresentation(
            charactersPerSecond = 10f,
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)

        assertEquals("First", presentation.frame(1_999L)?.visibleText)
        assertEquals("", presentation.frame(2_100L)?.visibleText)
        assertEquals("S", presentation.frame(2_200L)?.visibleText)
    }

    @Test
    fun replacesTheMessageWhenAnewEntryArrives() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)

        assertEquals("First", presentation.frame(1_100L)?.text)
        assertEquals("Second", presentation.frame(2_800L)?.text)
    }

    @Test
    fun fullyFadedMessageDoesNotResurfaceAfterHiddenBoundary() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Old"), true, 1_000L)
        assertNull(presentation.frame(2_800L))
        presentation.update(listOf("Old"), false, 2_000L)
        presentation.update(listOf("Old"), true, 3_000L)

        assertNull(presentation.frame(3_100L))
    }

    @Test
    fun hiddenBoundaryKeepsPendingMessagesInReadableOrder() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)
        presentation.update(listOf("First", "Second"), false, 1_200L)
        presentation.update(listOf("First", "Second", "Third"), true, 1_300L)

        assertEquals("First", presentation.frame(1_300L)?.text)
        assertEquals("Second", presentation.frame(2_800L)?.text)
    }

    @Test
    fun separatorLetsTheCurrentMessageFinishItsReadableCycle() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First"), false, 1_100L)

        assertEquals("First", presentation.frame(1_100L)?.text)
    }

    @Test
    fun separatorWaitsBeforeStartingTheQueuedMessage() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), false, 1_100L)

        assertEquals("First", presentation.frame(1_300L)?.text)
        assertNull(presentation.frame(2_800L))
        presentation.update(listOf("First", "Second"), true, 2_900L)

        assertEquals("Second", presentation.frame(2_900L)?.text)
    }

    @Test
    fun aNewBattleReplacesThePreviousMessageImmediately() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Old battle"), true, 1_000L)
        presentation.update(listOf("New battle"), true, 1_100L)

        assertEquals("New battle", presentation.frame(1_100L)?.text)
    }

    @Test
    fun explicitResetAllowsAnIdenticalOpeningMessageToAppearAgain() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Battle started."), true, 1_000L)
        presentation.reset()
        presentation.update(listOf("Battle started."), true, 2_000L)

        assertEquals("Battle started.", presentation.frame(2_000L)?.text)
    }

    @Test
    fun skipsSnapshotHistoryButQueuesLiveMessagesInOrder() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Old 1", "Old 2", "Old 3"), true, 1_000L)
        presentation.update(listOf("Old 1", "Old 2", "Old 3", "New 1", "New 2"), true, 1_100L)

        assertEquals("Old 3", presentation.frame(1_100L)?.text)
        assertEquals("New 1", presentation.frame(2_800L)?.text)
        assertEquals("New 2", presentation.frame(4_600L)?.text)
    }

    @Test
    fun keepsTheLatestLineWhenAHistoryWindowAdvances() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("One", "Two", "Three"), true, 1_000L)
        presentation.update(listOf("Two", "Three", "Four"), true, 1_100L)

        assertEquals("Three", presentation.frame(1_100L)?.text)
        assertEquals("Four", presentation.frame(2_800L)?.text)
    }

    @Test
    fun skipsAReplacedHistorySnapshotWithoutASharedBoundary() {
        val presentation = BattleFeedPresentation()
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
        val presentation = BattleFeedPresentation(
            charactersPerSecond = 10f,
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)
        val beforePause = presentation.frame(1_200L)

        presentation.setPlaybackPaused(true, 1_200L)
        assertEquals(beforePause, presentation.frame(4_000L))

        presentation.setPlaybackPaused(false, 4_000L)
        assertEquals("First", presentation.frame(4_100L)?.text)
        assertEquals("Fir", presentation.frame(4_100L)?.visibleText)
    }

    @Test
    fun keepsEventsArrivingWhilePausedInOrderAfterResume() {
        val presentation = BattleFeedPresentation(
            charactersPerSecond = 10f,
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
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Current"), true, 1_000L)
        presentation.update(listOf("Current") + (1..8).map { "Event $it" }, true, 1_100L)

        assertEquals("Event 1", presentation.frame(2_900L)?.text)
        assertEquals("Event 2", presentation.frame(4_800L)?.text)
    }

    @Test
    fun tapCompletesTheCurrentRevealBeforeMovingOn() {
        val presentation = BattleFeedPresentation(
            charactersPerSecond = 10f,
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)

        presentation.advanceOnTap(1_100L)

        assertEquals("First", presentation.frame(1_100L)?.visibleText)
        assertEquals("First", presentation.frame(1_500L)?.visibleText)
    }

    @Test
    fun secondTapAdvancesToTheNextQueuedMessage() {
        val presentation = BattleFeedPresentation(
            charactersPerSecond = 10f,
            minimumMessageDurationMillis = 0L,
            holdDurationMillis = 500L,
            fadeDurationMillis = 100L
        )
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)

        presentation.advanceOnTap(1_200L)
        presentation.advanceOnTap(1_300L)

        assertEquals("Second", presentation.frame(1_300L)?.text)
        assertEquals("", presentation.frame(1_300L)?.visibleText)
    }

    @Test
    fun resetStartsReadableFeedClockAfterPause() {
        val presentation = BattleFeedPresentation()
        presentation.setPlaybackPaused(true, 1_000L)
        presentation.reset()
        presentation.update(listOf("New battle"), true, 5_000L)

        assertEquals(0f, presentation.frame(5_000L)?.alpha)
    }
}
