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
        assertTrue(source.contains("val sprite = teamSprites[index]"))
        assertTrue(source.contains("teamStaticSprites[index]?.takeUnless { it === sprite }?.draw("))
        assertTrue(source.contains("requestedTeamSprites[index] == request"))
        assertTrue(source.contains("session.isReplayMode()"))
    }

    @Test
    fun teamArtworkAlwaysAttemptsAnimatedResolutionBeforeStaticFallback() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()
        val request = source.substringAfter("private fun requestTeamSprite")
            .substringBefore("private fun acceptTeamSprite")

        assertTrue(request.contains("spriteCache.requestPokemon(request)"))
        assertTrue(request.contains("spriteCache.requestStaticDexSprite(requestedSpecies, request.shiny)"))
        assertFalse(request.contains("if (!memoryConstrained)"))
    }

    @Test
    fun ignoresLateGimmickArtworkCallbacksAfterResourceRelease() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()

        assertTrue(source.contains("private var zPowerSymbolRequestToken = 0L"))
        assertTrue(source.contains("zPowerSymbolRequestToken += 1"))
        assertTrue(source.contains("if (requestToken != zPowerSymbolRequestToken) return@requestEffect"))
        assertTrue(source.contains("zPowerSymbol = null"))
        assertTrue(source.contains("zPowerSymbolNeedsReload = true"))
        assertTrue(source.contains("fun refreshResourceRequests()"))
        assertTrue(source.contains("zPowerSymbolRequestPending"))
    }

    @Test
    fun moveDetailMetricsAvoidColoredOutlines() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()
        val detailCell = source.substringAfter("private fun drawMoveInfoCell")
            .substringBefore("private fun moveCategoryLabel")
        val battleConsole = source.substringAfter("private fun drawBattleConsole")
            .substringBefore("private fun drawTestFightButton")
        val compactMetrics = source.substringAfter("private fun drawCompactMetricLine")
            .substringBefore("private fun drawEffectSummary")
        val effectSummary = source.substringAfter("private fun drawEffectSummary")
            .substringBefore("private fun formatBoosts")

        assertFalse(detailCell.contains("Paint.Style.STROKE"))
        assertFalse(battleConsole.contains("Paint.Style.STROKE"))
        assertFalse(compactMetrics.contains("Paint.Style.STROKE"))
        assertFalse(effectSummary.contains("Paint.Style.STROKE"))
        assertFalse(detailCell.contains("canvas.drawRoundRect(bounds"))
        assertFalse(detailCell.contains("canvas.drawRoundRect(inner"))
        assertTrue(battleConsole.indexOf("paint.style = Paint.Style.FILL") < battleConsole.indexOf("canvas.drawRoundRect(bounds"))
        assertTrue(detailCell.contains("paint.shader = null"))
        assertTrue(detailCell.contains("Color.rgb(5, 17, 28)"))
        assertTrue(detailCell.contains("canvas.drawRect(bounds, paint)"))
        assertTrue(detailCell.contains("Color.argb(72, 172, 180, 186)"))
        assertTrue(detailCell.contains("Color.rgb(216, 227, 232)"))

        val moveDetails = source.substringAfter("private fun drawMoveDetails")
            .substringBefore("private fun drawCompactMoveDetails")
        assertTrue(moveDetails.contains("paint.style = Paint.Style.FILL\n        paint.shader = null\n        paint.color = Color.argb(178, 3, 14, 24)"))
    }

    @Test
    fun moveTouchBoundsAreUnavailableWhileWaitingForTheNextRequest() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()
        val layout = source.substringAfter("private fun layoutMoveTouchBounds").substringBefore("private fun layoutReplayControlTouchBounds")

        assertTrue(layout.contains("if (!session.decisionAvailable) return"))
    }
}
