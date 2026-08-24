package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownTeamStatSummaryTest {
    @Test
    fun describesRemainingEvBudget() {
        assertEquals(
            "EV total 508/510 · 2 remaining",
            ShowdownTeamStatSummary.evs(listOf(252, 0, 0, 252, 0, 4))
        )
    }

    @Test
    fun flagsEvTotalsAndPerStatValuesAboveTheCompetitiveLimit() {
        assertEquals(
            "EV total 1008/510 · over limit",
            ShowdownTeamStatSummary.evs(listOf(252, 252, 252, 252, 0, 0))
        )
    }

    @Test
    fun acceptsShowdownMaximumEvValue() {
        assertEquals(
            "EV total 255/510 · 255 remaining",
            ShowdownTeamStatSummary.evs(listOf(255, 0, 0, 0, 0, 0))
        )
    }

    @Test
    fun flagsAnEvValueAboveTheShowdownPerStatLimit() {
        assertEquals(
            "EV total 256/510 · over limit",
            ShowdownTeamStatSummary.evs(listOf(256, 0, 0, 0, 0, 0))
        )
    }

    @Test
    fun countsPerfectIvs() {
        assertEquals(
            "IVs 155/186 · 5 perfect",
            ShowdownTeamStatSummary.ivs(listOf(31, 31, 31, 0, 31, 31))
        )
    }

    @Test
    fun flagsAnIvValueAboveThePerStatLimit() {
        assertEquals(
            "IVs 187/186 · invalid value",
            ShowdownTeamStatSummary.ivs(listOf(32, 31, 31, 31, 31, 31))
        )
    }
}
