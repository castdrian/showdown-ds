package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSceneViewContractTest {
    @Test
    fun keepsPlayerAndOpponentSpriteOrientationDistinct() {
        val source = File("src/main/kotlin/dev/adrian/showdown/BattleSceneView.kt").readText()

        assertTrue(source.contains("spriteCache.requestPokemon(playerSpecies, true, session.spriteStyle)"))
        assertTrue(source.contains("spriteCache.requestPokemon(opponentSpecies, false, session.spriteStyle)"))
        assertTrue(source.contains("requestActiveSprites(session.playerActiveCombatants(), true"))
        assertTrue(source.contains("requestActiveSprites(session.opponentActiveCombatants(), false"))
        assertTrue(source.contains("combatant.species.ifBlank { combatant.name }"))
        assertTrue(source.contains("spriteCache.requestPokemon(species, back, session.spriteStyle)"))
        assertTrue(source.contains("playerActiveSprites[combatant.slot]"))
        assertTrue(source.contains("opponentActiveSprites[combatant.slot]"))
        assertTrue(source.contains("if (pokemonOverride != null)"))
        assertTrue(source.contains("val playerSpecies = session.playerActiveCombatants().firstOrNull()?.species"))
        assertTrue(source.contains("val opponentSpecies = session.opponentActiveCombatants().firstOrNull()?.species"))
        assertTrue(source.contains("session.opponentCondition,\n                session.opponentEntryAtNanos,\n                nowNanos\n            )"))
        assertTrue(source.contains("session.playerCondition,\n                session.playerEntryAtNanos,\n                nowNanos\n            )"))
        assertTrue(source.contains("playerSprite ?: playerPlaceholder"))
        assertTrue(source.contains("opponentSprite ?: opponentPlaceholder"))
        assertTrue(source.contains("BattleSession.displayPokemonName(session.playerDetails().name, session.playerDetails().species)"))
        assertTrue(source.contains("BattleSession.displayPokemonName(session.opponentDetails().name, session.opponentDetails().species)"))
        assertTrue(source.contains("BattleSession.displayPokemonName(combatant.name, combatant.species)"))
        assertTrue(source.contains("if (player) combatants else combatants.asReversed()"))
    }
}
