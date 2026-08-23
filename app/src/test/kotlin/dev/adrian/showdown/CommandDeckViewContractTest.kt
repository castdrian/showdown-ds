package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class CommandDeckViewContractTest {
    @Test
    fun teamArtworkIsKeyedByStablePartyPosition() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("private val teamSprites = mutableMapOf<Int, ShowdownSpriteCache.SpriteAsset>()"))
        assertTrue(source.contains("private val requestedTeamSprites = mutableMapOf<Int, BattleSpriteRequest>()"))
        assertTrue(source.contains("requestTeamSprite(index, details.species.ifBlank { pokemon }, details.shiny)"))
        assertTrue(source.contains("teamSprites[index]?.draw("))
        assertTrue(source.contains("requestedTeamSprites[index] == request"))
    }
}
