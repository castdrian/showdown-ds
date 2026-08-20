package dev.adrian.showdown

import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownLadderQueryTest {
    @Test
    fun includesRankPlayerAndRatingMetadata() {
        val text = ShowdownLadderQuery.searchText(
            4,
            ShowdownLobbyState.LadderEntry("Alice", 1823.4, 71.2, 1840.0, 55.0, 12.0)
        )

        assertTrue(text.startsWith("5 Alice"))
        assertTrue(text.contains("Alice"))
        assertTrue(text.contains("1823.4"))
        assertTrue(text.contains("71.2"))
    }
}
