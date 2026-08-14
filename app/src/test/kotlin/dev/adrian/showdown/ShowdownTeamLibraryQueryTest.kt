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
    fun listsDistinctFormatsForTeamLibraryNavigation() {
        assertEquals(listOf("gen8ou", "gen9ou"), ShowdownTeamLibraryQuery.formats(teams))
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

    @Test
    fun filtersByFormatWithoutChangingFolderSearch() {
        assertEquals(
            listOf("Rain", "Balance"),
            ShowdownTeamLibraryQuery.filter(teams, ShowdownTeamLibraryFilter(format = "gen9ou")).map { it.name }
        )
        assertEquals(
            listOf("Rain"),
            ShowdownTeamLibraryQuery.filter(
                teams,
                ShowdownTeamLibraryFilter(query = "hurricane", format = "gen9ou", folder = "Weather")
            ).map { it.name }
        )
    }
}
