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
        assertTrue(source.contains("nativeMoveStarted();"))
        assertTrue(source.contains("nativeBattleStarted();"))
        assertTrue(source.contains("new MutationObserver(function ()"))
        assertTrue(source.contains("chromeObserver.observe(document.getElementById('battle'), { childList: true, subtree: true });"))
        assertTrue(source.contains("release: function ()"))
        assertFalse(source.contains("requestAnimationFrame(keepChromeHidden)"))
        assertFalse(source.contains("var hideFrame = 0;"))
        assertTrue(source.contains("this.__showdownNativeDamageArmed = !move || move.category !== 'Status';"))
        assertTrue(source.contains("this.__showdownNativeDamagePlayed = false;"))
        assertTrue(source.contains("this.__showdownNativeDamagePending && this.__showdownNativeDamageArmed && !this.__showdownNativeDamagePlayed"))
        assertTrue(source.contains("nativeCue('generic_damage');"))
        assertTrue(source.contains("var originalHealAnim = BattleScene.prototype.healAnim;"))
        assertTrue(source.contains("(args[0] === '-damage' || args[0] === '-sethp') && !kwArgs.from && this.scene.__showdownNativeDamageArmed"))
        assertTrue(source.contains("args[0] === '-clearpositiveboost'"))
        assertTrue(source.contains("args[0] === '-clearnegativeboost'"))
        assertTrue(source.contains("fun battleStarted()"))
        assertTrue(source.contains("resetAudio()"))
        assertTrue(source.contains("fun moveStarted()"))
        assertTrue(source.contains("resetMoveAudio()"))
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
