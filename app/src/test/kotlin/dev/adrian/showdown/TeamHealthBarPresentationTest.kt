package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamHealthBarPresentationTest {
    @Test
    fun faintedConditionOverridesStaleFullHp() {
        val health = TeamHealthBarPresentation.from("100/100", "FNT")

        assertEquals(0f, health.fraction, 0.001f)
        assertEquals("0/100", health.label)
    }

    @Test
    fun healthyTeamMemberKeepsItsHpFractionAndLabel() {
        val health = TeamHealthBarPresentation.from("62/100", "62/100")

        assertEquals(0.62f, health.fraction, 0.001f)
        assertEquals("62/100", health.label)
    }
}
