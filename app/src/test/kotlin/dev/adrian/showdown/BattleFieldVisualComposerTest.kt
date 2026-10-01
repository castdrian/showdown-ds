package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFieldVisualComposerTest {
    @Test
    fun supportedWeatherMapsToAnimatedLayers() {
        val expected = mapOf(
            "RainDance" to BattleWeatherVisual.RAIN,
            "Sun" to BattleWeatherVisual.SUN,
            "Sandstorm" to BattleWeatherVisual.SANDSTORM,
            "Hail" to BattleWeatherVisual.SNOW,
            "Snow" to BattleWeatherVisual.SNOW
        )

        expected.forEach { (weather, visual) ->
            assertEquals(visual, BattleFieldVisualComposer.compose(battleInfo(weather = weather)).weather)
        }
    }

    @Test
    fun supportedTerrainAndFieldEffectsMapToVisibleLayers() {
        val result = BattleFieldVisualComposer.compose(
            battleInfo(
                terrain = "Psychic Terrain",
                fieldEffects = listOf("Gravity", "Trick Room", "Magic Room", "Wonder Room", "Unknown")
            )
        )

        assertEquals(BattleTerrainVisual.PSYCHIC, result.terrain)
        assertEquals(
            setOf(
                BattleFieldOverlay.GRAVITY,
                BattleFieldOverlay.TRICK_ROOM,
                BattleFieldOverlay.MAGIC_ROOM,
                BattleFieldOverlay.WONDER_ROOM
            ),
            result.overlays
        )
        assertTrue(result.hasActiveVisuals)
    }

    @Test
    fun unknownOrClearedConditionsProduceNoAnimatedLayers() {
        val result = BattleFieldVisualComposer.compose(
            battleInfo(weather = "", terrain = "Unknown Terrain", fieldEffects = emptyList())
        )

        assertEquals(null, result.weather)
        assertEquals(null, result.terrain)
        assertTrue(result.overlays.isEmpty())
        assertFalse(result.hasActiveVisuals)
    }

    private fun battleInfo(
        weather: String = "",
        terrain: String = "",
        fieldEffects: List<String> = emptyList()
    ) = BattleSession.BattleInfo(
        weather = weather,
        terrain = terrain,
        playerSideConditions = emptyList(),
        opponentSideConditions = emptyList(),
        playerBoosts = emptyMap(),
        opponentBoosts = emptyMap(),
        fieldEffects = fieldEffects
    )
}
