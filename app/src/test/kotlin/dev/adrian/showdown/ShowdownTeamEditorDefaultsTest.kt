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
            BattleSession.MatchFormat("gen9doublesou", "[Gen 9] Doubles OU", canSearch = true, canChallenge = true)
        )
        assertEquals(
            "gen9doublesou",
            ShowdownTeamEditorDefaults.format(null, BattleSession.MatchFormat.GEN9_RANDOM, formats)
        )
    }

    @Test
    fun usableFormatPrefersSearchAndChallengeOverChallengeOnly() {
        val formats = listOf(
            BattleSession.MatchFormat("gen9challengeonly", "Challenge only", canSearch = false, canChallenge = true),
            BattleSession.MatchFormat("gen9ou", "[Gen 9] OU", canSearch = true, canChallenge = true)
        )
        assertEquals("gen9ou", ShowdownTeamEditorDefaults.format(null, BattleSession.MatchFormat.GEN9_RANDOM, formats))
    }

    @Test
    fun searchableOnlyFormatIsStillUsable() {
        val formats = listOf(
            BattleSession.MatchFormat("gen9searchonly", "Search only", canSearch = true, canChallenge = false)
        )
        assertEquals("gen9searchonly", ShowdownTeamEditorDefaults.format(null, BattleSession.MatchFormat.GEN9_RANDOM, formats))
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
