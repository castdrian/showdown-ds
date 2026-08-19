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
    fun canonicalizesLegacyFormatsForTeamLibraryNavigation() {
        val legacyTeams = teams + ShowdownTeam("legacy", "Legacy", "HD matchup", "|Pikachu|||thunderbolt")

        assertEquals(
            listOf("gen8ou", "gen9ou", "gen9randombattle"),
            ShowdownTeamLibraryQuery.formats(legacyTeams)
        )
    }

    @Test
    fun formatsUseReadableShowdownLabelsWhenTheServerLabelIsUnavailable() {
        assertEquals("[Gen 9] Random Battle", ShowdownTeamLibraryQuery.displayFormat(" gen9randombattle "))
        assertEquals("[Gen 9] OU", ShowdownTeamLibraryQuery.displayFormat("gen9ou"))
    }

    @Test
    fun prefersAdvertisedFormatLabelsForChallengeAndTeamSurfaces() {
        val advertised = listOf(
            BattleSession.MatchFormat(" gen9ou ", " [Gen 9] OverUsed ", menuLabel = " gen9ou "),
            BattleSession.MatchFormat("gen9randombattle", "gen9randombattle", usesRandomTeams = true)
        )

        assertEquals("[Gen 9] OverUsed", ShowdownTeamLibraryQuery.displayFormat(" GEN9OU ", advertised))
        assertEquals("[Gen 9] Random Battle", ShowdownTeamLibraryQuery.displayFormat("gen9randombattle", advertised))
        assertEquals("[Gen 9] Ubers", ShowdownTeamLibraryQuery.displayFormat("gen9ubers", advertised))
        assertEquals("gen9ou", ShowdownTeamLibraryQuery.resolveFormat(" GEN9OU ", advertised)?.id?.trim())
        assertEquals(true, ShowdownTeamLibraryQuery.resolveFormat("gen9randombattle", advertised)?.usesRandomTeams)
        assertEquals(null, ShowdownTeamLibraryQuery.resolveFormat("gen9ubers", advertised))

        val normalized = ShowdownTeamLibraryQuery.matchFormat(
            " gen9ou ",
            listOf(BattleSession.MatchFormat("gen9ou", "gen9ou", usesRandomTeams = false))
        )
        assertEquals("gen9ou", normalized.id)
        assertEquals("[Gen 9] OU", normalized.label)
        assertEquals("[Gen 9] OU", normalized.menuLabel)
        assertEquals(false, normalized.usesRandomTeams)
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
        assertEquals(
            listOf("Rain"),
            ShowdownTeamLibraryQuery.filter(
                teams,
                ShowdownTeamLibraryFilter(format = " gen9ou ", folder = " weather ")
            ).map { it.name }
        )
    }

    @Test
    fun matchingFormatAcceptsWhitespacePaddedStoredAndRequestedIds() {
        assertEquals(
            listOf("Rain", "Balance", "Legacy"),
            ShowdownTeamLibraryQuery.matchingFormat(
                teams + ShowdownTeam("legacy", "Legacy", " gen9ou ", "|Politoed|||scald"),
                " GEN9OU "
            ).map { it.name }
        )
    }

    @Test
    fun matchingFormatFindsTeamsSavedWithLegacyHdMatchupMetadata() {
        val legacy = ShowdownTeam("legacy", "Legacy", "HD matchup", "|Pikachu|||thunderbolt")

        assertEquals(
            listOf("Legacy"),
            ShowdownTeamLibraryQuery.matchingFormat(listOf(legacy), "gen9randombattle").map { it.name }
        )
    }
}
