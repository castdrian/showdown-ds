package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownMoveEffectsContractTest {
    @Test
    fun incrementalBattleStartsPausedAndUsesTheNativePlaybackClock() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownMoveEffectsView.kt").readText()

        assertTrue(source.contains("new Battle({ id: 'showdownds', paused: true,"))
        assertTrue(source.contains("if (battle.paused) battle.play();"))
        assertTrue(source.contains("this.__showdownNativeResultCues = [];"))
        assertTrue(source.contains("if (!this.scene.__showdownNativeResultCues) this.scene.__showdownNativeResultCues = [];"))
        assertTrue(source.contains("this.__showdownNativeResultCues && this.__showdownNativeResultCues.length"))
        assertTrue(source.contains("this.__showdownNativeResultCues.shift();"))
        assertTrue(source.contains("this.scene.__showdownNativeResultCues.push(resultCue);"))
        assertFalse(source.contains("__showdownNativeResultCue = null"))
        assertTrue(source.contains("this.scene.__showdownNativeAudioSilent = !!kwArgs.silent;"))
        assertTrue(source.contains("seed(protocolHistoryProvider())"))
        assertTrue(source.contains("flushPendingPackets(allowSeedWhilePaused = true)"))
        assertFalse(source.contains("if (resultCue && this.scene.__showdownNativeResultCue === resultCue)"))
        assertFalse(source.contains("BattlePlaybackTiming.pauseAfter(packet)"))
        assertFalse(source.contains("postDelayed(flushRunnable"))
    }

    @Test
    fun pausedReplayAppliesItsInitialChunkBeforePausingPlayback() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val enqueueIndex = source.indexOf("enqueueBattlePlayback(null, null, replay.log.lines(), resetOnBattleInit = false)")
        val pauseIndex = source.indexOf("if (replayStartsPaused) setReplayPaused(true)", enqueueIndex)

        assertTrue(enqueueIndex >= 0)
        assertTrue(pauseIndex > enqueueIndex)
    }
}
