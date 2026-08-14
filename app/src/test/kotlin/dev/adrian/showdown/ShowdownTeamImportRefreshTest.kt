package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTeamImportRefreshTest {
    @Test
    fun refreshesVisibleLibraryOnlyWhenImportAddsTeams() {
        val team = ShowdownTeam("id", "Team", "gen9ou", "|Pikachu|||||||||||")

        assertTrue(ShowdownTeamImportRefresh.shouldRefresh(true, false, listOf(team)))
        assertTrue(ShowdownTeamImportRefresh.shouldRefresh(false, true, listOf(team)))
        assertFalse(ShowdownTeamImportRefresh.shouldRefresh(false, false, listOf(team)))
        assertFalse(ShowdownTeamImportRefresh.shouldRefresh(true, false, emptyList()))
    }
}
