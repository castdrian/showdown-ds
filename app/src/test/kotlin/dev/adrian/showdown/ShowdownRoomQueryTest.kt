package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownRoomQueryTest {
    @Test
    fun matchesRoomPlayerAndFormatTermsAcrossVisibleMetadata() {
        assertTrue(ShowdownRoomQuery.matches("battle alice gen9ou", "battle-gen9ou-1", "Battle room", "Alice vs. Bob · Gen 9 OU"))
        assertTrue(ShowdownRoomQuery.matches("lobby", "lobby", "Lobby", "Official · 42 online"))
        assertFalse(ShowdownRoomQuery.matches("battle charlie", "battle-gen9ou-1", "Battle room", "Alice vs. Bob · Gen 9 OU"))
    }

    @Test
    fun blankQueryKeepsEveryRoomVisible() {
        assertTrue(ShowdownRoomQuery.matches("  ", "lobby", "Lobby", "Official"))
    }
}
