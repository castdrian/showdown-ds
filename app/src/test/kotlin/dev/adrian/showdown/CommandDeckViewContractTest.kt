package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertFalse
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

    @Test
    fun moveDetailMetricsAvoidColoredOutlines() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()
        val detailCell = source.substringAfter("private fun drawMoveInfoCell")
            .substringBefore("private fun moveCategoryLabel")
        val compactMetrics = source.substringAfter("private fun drawCompactMetricLine")
            .substringBefore("private fun drawEffectSummary")

        assertFalse(detailCell.contains("Paint.Style.STROKE"))
        assertFalse(compactMetrics.contains("Paint.Style.STROKE"))
        assertTrue(detailCell.contains("Color.argb(236, 24, 31, 38)"))
        assertTrue(detailCell.contains("Color.argb(88, 166, 174, 180)"))
        assertTrue(detailCell.contains("Color.rgb(216, 227, 232)"))
    }
}
