package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleFeedPresentationTest {
    @Test
    fun startsWithOnlyTheNewestMessage() {
        val presentation = BattleFeedPresentation()

        presentation.update(listOf("Older", "Newest"), true, 1_000L)

        assertEquals("Newest", presentation.frame(1_100L)?.text)
    }

    @Test
    fun fadesAnUnchangedMessageAway() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Move text"), true, 1_000L)

        assertEquals(1f, presentation.frame(1_500L)?.alpha)
        assertEquals(0.23f, presentation.frame(4_800L)?.alpha ?: 0f, 0.01f)
        assertNull(presentation.frame(4_900L))
    }

    @Test
    fun replacesTheMessageWhenAnewEntryArrives() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("First"), true, 1_000L)
        presentation.update(listOf("First", "Second"), true, 1_100L)

        assertEquals("First", presentation.frame(1_100L)?.text)
        assertEquals("Second", presentation.frame(2_300L)?.text)
    }

    @Test
    fun hiddenFeedDoesNotResurfaceOldHistory() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Old"), true, 1_000L)
        presentation.update(listOf("Old"), false, 2_000L)
        presentation.update(listOf("Old"), true, 3_000L)

        assertNull(presentation.frame(3_100L))
    }

    @Test
    fun restoresWithOnlyTheNewestMessageButQueuesNewMessages() {
        val presentation = BattleFeedPresentation()
        presentation.update(listOf("Old 1", "Old 2", "Old 3"), true, 1_000L)
        presentation.update(listOf("Old 1", "Old 2", "Old 3", "New 1", "New 2"), true, 1_100L)

        assertEquals("Old 3", presentation.frame(1_100L)?.text)
        assertEquals("New 1", presentation.frame(2_300L)?.text)
    }
}
