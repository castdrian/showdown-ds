package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleMoveVisualStyleResolverTest {
    @Test
    fun statusMovesNeverUseDamagingMoveStyles() {
        assertEquals(
            BattleMoveVisualStyle.STATUS,
            BattleMoveVisualStyleResolver.resolve(BattleSession.MoveInfo("—", "—", "Status"), "FIRE")
        )
    }

    @Test
    fun contactMovesUseASeparateMeleeAnimation() {
        assertEquals(
            BattleMoveVisualStyle.CONTACT_STRIKE,
            BattleMoveVisualStyleResolver.resolve(
                BattleSession.MoveInfo("80", "100", "Physical", contact = true),
                "FIGHTING"
            )
        )
    }

    @Test
    fun damagingTypesUseDistinctAnimationStyles() {
        val expected = mapOf(
            "FIRE" to BattleMoveVisualStyle.FIRE_BURST,
            "WATER" to BattleMoveVisualStyle.WATER_WAVE,
            "ELECTRIC" to BattleMoveVisualStyle.ELECTRIC_ARC,
            "GROUND" to BattleMoveVisualStyle.GROUND_RIPPLE,
            "ICE" to BattleMoveVisualStyle.ICE_SHARDS,
            "GRASS" to BattleMoveVisualStyle.LEAF_SPIRAL,
            "PSYCHIC" to BattleMoveVisualStyle.PSYCHIC_PULSE,
            "FLYING" to BattleMoveVisualStyle.WIND_CRESCENT
        )

        expected.forEach { (type, style) ->
            assertEquals(
                type,
                style,
                BattleMoveVisualStyleResolver.resolve(BattleSession.MoveInfo("90", "100", "Special"), type)
            )
        }
    }
}
