package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownTeamLibraryQueryTest {
    private val teams = listOf(
        ShowdownTeam("one", "Rain", "gen9ou", "|Pelipper|||hurricane", folder = "Weather"),
        ShowdownTeam("two", "Balance", "gen9ou", "|Gholdengo|||makeitrain", folder = "Tournament"),
        ShowdownTeam("three", "Battle", "gen8ou", "|Landorus|||earthquake")
    )

    @Test
    fun listsDistinctFoldersWithoutTheUnfiledPlaceholder() {
        assertEquals(listOf("Tournament", "Weather"), ShowdownTeamLibraryQuery.folders(teams))
    }

    @Test
    fun filtersByFolderAndCommaSeparatedPackedTerms() {
        assertEquals(
            listOf("Rain"),
            ShowdownTeamLibraryQuery.filter(teams, ShowdownTeamLibraryFilter(folder = "Weather")).map { it.name }
        )
        assertEquals(
            listOf("Balance"),
            ShowdownTeamLibraryQuery.filter(teams, ShowdownTeamLibraryFilter(query = "gen9ou, makeitrain")).map { it.name }
        )
    }
}
