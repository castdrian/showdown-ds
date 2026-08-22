package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplayControlPresentationTest {
    @Test
    fun replayControlsDescribePausedAndPlayingStatesWithoutGenericBattlePlaceholderText() {
        assertEquals("Pause replay", ReplayControlPresentation.pauseLabel(paused = false))
        assertEquals("Resume replay", ReplayControlPresentation.pauseLabel(paused = true))
        assertEquals("Playing · 0.75×", ReplayControlPresentation.statusLabel(paused = false, speed = 0.75f))
        assertEquals("Paused · 1×", ReplayControlPresentation.statusLabel(paused = true, speed = 1f))
    }

    @Test
    fun replayControlsOfferEverySupportedPlaybackSpeedInOrder() {
        assertEquals(listOf(0.5f, 0.75f, 1f, 1.5f, 2f), ReplayControlPresentation.speeds)
    }

    @Test
    fun replayFightPanelUsesPlaybackControlsBeforeTheLiveBattleWaitingState() {
        val source = File("src/main/kotlin/dev/adrian/showdown/CommandDeckView.kt").readText()
        val drawMoves = source.substringAfter("private fun drawMoves")
            .substringBefore("private fun hasReplayControls")

        assertTrue(drawMoves.contains("drawReplayControls(canvas, width, height, scale)"))
        assertTrue(
            drawMoves.indexOf("drawReplayControls(canvas, width, height, scale)") <
                drawMoves.indexOf("\"Waiting for battle data\"")
        )
    }
}
