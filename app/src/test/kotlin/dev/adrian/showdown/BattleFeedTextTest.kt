package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFeedTextTest {
    @Test
    fun keepsAReadableBattleEventAcrossTwoLines() {
        val event = "The opposing Pokémon fainted"
        val measure: (String) -> Float = { it.length * 10f }
        val lines = BattleFeedText.wrap(event, 160f, 2, measure)

        assertEquals(event, lines.joinToString(" "))
        assertTrue(lines.size <= 2)
        assertTrue(lines.all { measure(it) <= 160f })
    }

    @Test
    fun newestWindowKeepsCompleteEventsTogether() {
        val entries = listOf(
            listOf("Battle timer is ON", "inactive players will lose"),
            listOf("A move was used."),
            listOf("The target lost health."),
            listOf("The battle is waiting.")
        )

        assertEquals(
            listOf("A move was used.", "The target lost health.", "The battle is waiting."),
            BattleFeedText.window(entries, 3, 0)
        )
    }

    @Test
    fun scrolledWindowAlsoEndsOnCompleteEvents() {
        val entries = listOf(
            listOf("Battle timer is ON", "inactive players will lose"),
            listOf("A move was used."),
            listOf("The target lost health."),
            listOf("The battle is waiting.")
        )

        assertEquals(
            listOf("Battle timer is ON", "inactive players will lose", "A move was used."),
            BattleFeedText.window(entries, 3, 3)
        )
    }

    @Test
    fun longEntriesRemainScrollableInsteadOfBeingDiscarded() {
        val entries = listOf(
            listOf("A very long battle message line one", "line two", "line three", "line four"),
            listOf("The next battle event.")
        )

        assertEquals(
            listOf("line two", "line three", "line four"),
            BattleFeedText.window(entries, 3, 1)
        )
        assertEquals(
            listOf("A very long battle message line one", "line two", "line three"),
            BattleFeedText.window(entries, 3, 2)
        )
    }

    @Test
    fun mixedLongAndShortEntriesNeverSplitTheShortEntry() {
        val entries = listOf(
            listOf("Long event line one", "line two", "line three", "line four"),
            listOf("Short event line one", "line two")
        )

        assertEquals(
            listOf("line four", "Short event line one", "line two"),
            BattleFeedText.window(entries, 3, 0)
        )
        assertEquals(
            listOf("line two", "line three", "line four"),
            BattleFeedText.window(entries, 3, 1)
        )
    }

    @Test
    fun longEntryScrollPositionsAdvanceUntilTheHistoryEnds() {
        val entries = listOf(
            listOf("Long event line one", "line two", "line three", "line four"),
            listOf("Short event line one", "line two")
        )

        val windows = (0..2).map { scroll -> BattleFeedText.window(entries, 3, scroll) }

        assertEquals(
            listOf(
                listOf("line four", "Short event line one", "line two"),
                listOf("line two", "line three", "line four"),
                listOf("Long event line one", "line two", "line three")
            ),
            windows
        )
    }

    @Test
    fun mixedHistoryKeepsOlderNormalEntriesReachable() {
        val entries = listOf(
            listOf("A"),
            listOf("B1", "B2"),
            listOf("C"),
            listOf("D1", "D2", "D3")
        )

        val windows = (0..5).map { scroll -> BattleFeedText.window(entries, 2, scroll) }

        assertTrue(entries.flatten().all { line -> windows.flatten().contains(line) })
    }

    @Test
    fun battleFeedWrappingPreservesLongUnbrokenTokens() {
        assertEquals(
            listOf("ABCD", "EFGH", "IJ"),
            BattleFeedText.wrapForBattleFeed("ABCDEFGHIJ", 4f) { it.length.toFloat() }
        )
        assertEquals(
            listOf("ABCD", "EFG…"),
            BattleFeedText.wrap("ABCDEFGHIJ", 4f, 2) { it.length.toFloat() }
        )
    }

    @Test
    fun battleFeedWrappingPreservesUnicodeCodePoints() {
        val lines = BattleFeedText.wrapForBattleFeed("😀😀😀", 2f) { it.codePointCount(0, it.length).toFloat() }

        assertEquals(listOf("😀😀", "😀"), lines)
        assertEquals("😀😀😀", lines.joinToString(""))
    }
}
