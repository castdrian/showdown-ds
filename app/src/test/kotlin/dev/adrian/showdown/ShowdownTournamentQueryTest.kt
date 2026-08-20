package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTournamentQueryTest {
    private val tournament = ShowdownTournamentDirectoryState.TournamentSummary(
        roomId = "gen9ou-123",
        roomName = "gen9ou-123",
        format = "OU Single Elimination",
        generator = "Single Elimination",
        started = false,
        playerCount = 4
    )

    @Test
    fun matchesRoomFormatStatusAndPlayerCountMetadata() {
        assertTrue(ShowdownTournamentQuery.matches("gen9ou single", tournament))
        assertTrue(ShowdownTournamentQuery.matches("accepting 4", tournament))
        assertFalse(ShowdownTournamentQuery.matches("started", tournament))
    }

    @Test
    fun blankQueryKeepsEveryTournamentVisible() {
        assertTrue(ShowdownTournamentQuery.matches("  ", tournament))
    }
}
