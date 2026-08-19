package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownFormatCompatibilityTest {
    @Test
    fun mapsLegacyHdMatchupIdsAndLabelsToGen9RandomBattle() {
        assertTrue(ShowdownFormatCompatibility.isLegacyHdMatchup("HD matchup"))
        assertEquals("gen9randombattle", ShowdownFormatCompatibility.canonicalId("hdmatchup"))
        assertEquals("gen9randombattle", ShowdownFormatCompatibility.canonicalId("gen9randombattle", "HD matchup"))
        assertEquals(
            BattleSession.MatchFormat.GEN9_RANDOM,
            ShowdownFormatCompatibility.canonical(BattleSession.MatchFormat("hdmatchup", "HD matchup"))
        )
    }

    @Test
    fun canonicalizesLegacyFormatTextInWaitingStatus() {
        assertEquals(
            "[Gen 9] Random Battle challenge sent to Gladion.",
            ShowdownFormatCompatibility.canonicalizeLegacyText("HD matchup challenge sent to Gladion.")
        )
    }

    @Test
    fun preservesValidFormatIdentityAndLabels() {
        val format = BattleSession.MatchFormat(" gen9ou ", " [Gen 9] OU ", " OU ")

        assertFalse(ShowdownFormatCompatibility.isLegacyHdMatchup(format.id))
        assertEquals(
            BattleSession.MatchFormat("gen9ou", "[Gen 9] OU", "OU"),
            ShowdownFormatCompatibility.canonical(format)
        )
    }

    @Test
    fun teamFormatPresentationUsesCanonicalLegacyFormat() {
        val knownFormats = listOf(BattleSession.MatchFormat.GEN9_RANDOM)

        assertEquals("[Gen 9] Random Battle", ShowdownTeamLibraryQuery.displayFormat("hdmatchup", knownFormats))
        assertEquals("gen9randombattle", ShowdownTeamLibraryQuery.matchFormat("HD matchup", knownFormats).id)
    }
}
