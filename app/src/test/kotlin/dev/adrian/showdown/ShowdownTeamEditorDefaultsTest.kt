package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownTeamEditorDefaultsTest {
    @Test
    fun explicitFormatWins() {
        assertEquals(
            "gen9uu",
            ShowdownTeamEditorDefaults.format(
                initialFormat = " gen9uu ",
                current = BattleSession.MatchFormat.GEN9_RANDOM,
                available = emptyList()
            )
        )
    }

    @Test
    fun currentCompetitiveFormatIsKept() {
        val current = BattleSession.MatchFormat("gen9ou", "[Gen 9] OU")
        assertEquals("gen9ou", ShowdownTeamEditorDefaults.format(null, current, emptyList()))
    }

    @Test
    fun advertisedCompetitiveFormatBeatsRandomCurrentFormat() {
        val formats = listOf(
            BattleSession.MatchFormat.GEN9_RANDOM,
            BattleSession.MatchFormat("gen9doublesou", "[Gen 9] Doubles OU")
        )
        assertEquals(
            "gen9doublesou",
            ShowdownTeamEditorDefaults.format(null, BattleSession.MatchFormat.GEN9_RANDOM, formats)
        )
    }

    @Test
    fun fallsBackToGen9OuWhenOnlyRandomFormatsAreKnown() {
        assertEquals(
            "gen9ou",
            ShowdownTeamEditorDefaults.format(
                initialFormat = null,
                current = BattleSession.MatchFormat.GEN9_RANDOM,
                available = BattleSession.MatchFormat.defaults
            )
        )
    }
}
