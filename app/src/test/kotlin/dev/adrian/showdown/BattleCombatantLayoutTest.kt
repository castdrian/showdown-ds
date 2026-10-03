package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleCombatantLayoutTest {
    @Test
    fun selectsTheOnlyLivingCombatantWhenTriplesHaveBeenCentered() {
        val combatants = listOf(
            combatant("p1a", "0 fnt"),
            combatant("p1b"),
            combatant("p1c", "0 fnt")
        )

        assertEquals("p1b", BattleCombatantLayout.centeredSlot(true, combatants))
    }

    @Test
    fun doesNotCenterWhenTriplesHaveNotBeenCenteredOrMultipleCombatantsRemain() {
        val singleSurvivor = listOf(combatant("p1a"), combatant("p1b", "0 fnt"))
        val multipleSurvivors = listOf(combatant("p1a"), combatant("p1b"))

        assertNull(BattleCombatantLayout.centeredSlot(false, singleSurvivor))
        assertNull(BattleCombatantLayout.centeredSlot(true, multipleSurvivors))
    }

    @Test
    fun centersOnlyTheRemainingCombatantAndPreservesOtherLanePositions() {
        val width = 1000f

        assertEquals(
            320f,
            BattleCombatantLayout.x(width, true, 1, 3, "p1b", "p1b"),
            0.001f
        )
        assertEquals(
            200f,
            BattleCombatantLayout.x(width, true, 0, 3, "p1b", "p1a"),
            0.001f
        )
        assertEquals(
            760f,
            BattleCombatantLayout.x(width, false, 1, 3, "p2b", "p2b"),
            0.001f
        )
    }

    private fun combatant(slot: String, condition: String = "100/100") =
        BattleSession.ActiveCombatant(
            slot = slot,
            name = slot,
            types = emptyList(),
            level = "50",
            gender = "",
            hp = condition,
            condition = condition,
            entryAtNanos = 0L
        )
}
