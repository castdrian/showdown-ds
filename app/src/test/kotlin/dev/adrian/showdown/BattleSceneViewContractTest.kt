package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSceneViewContractTest {
    @Test
    fun keepsPlayerAndOpponentSpriteOrientationDistinct() {
        val source = File("src/main/kotlin/dev/adrian/showdown/BattleSceneView.kt").readText()

        assertTrue(source.contains("spriteCache.requestPokemon(session.playerPokemon, true, session.spriteStyle)"))
        assertTrue(source.contains("spriteCache.requestPokemon(session.opponentPokemon, false, session.spriteStyle)"))
        assertTrue(source.contains("requestActiveSprites(session.playerActiveCombatants(), true"))
        assertTrue(source.contains("requestActiveSprites(session.opponentActiveCombatants(), false"))
    }
}
