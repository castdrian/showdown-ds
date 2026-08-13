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
        assertTrue(source.contains("this.scene.__showdownNativeResultCues = [];"))
        assertTrue(source.contains("nativeMoveStarted();"))
        assertTrue(source.contains("var originalUseMove = Battle.prototype.useMove;"))
        assertTrue(source.contains("Battle.prototype.useMove = function (pokemon, move)"))
        assertTrue(source.contains("return originalUseMove.apply(this, arguments);"))
        assertTrue(source.contains("nativeBattleStarted();"))
        assertTrue(source.contains("function installBattleLogHooks()"))
        assertTrue(source.contains("BattleLog.prototype.addDiv = function (className, html)"))
        assertTrue(source.contains("BattleLog.prototype.addBattleMessage = function (args, kwArgs)"))
        assertTrue(source.contains("window.ShowdownNativeBattleLog.entry(lines.join('<br />'));"))
        assertTrue(source.contains("const val NATIVE_BATTLE_LOG_BRIDGE = \"ShowdownNativeBattleLog\""))
        assertTrue(source.contains("new MutationObserver(function ()"))
        assertTrue(source.contains("chromeObserver.observe(document.getElementById('battle'), { childList: true, subtree: true });"))
        assertTrue(source.contains(".result { display: none !important; }"))
        assertTrue(source.contains("release: function ()"))
        assertFalse(source.contains("requestAnimationFrame(keepChromeHidden)"))
        assertFalse(source.contains("var hideFrame = 0;"))
        assertTrue(source.contains("updateAcceleration.call(scene);"))
        assertTrue(source.contains("scene.acceleration = animationSpeed;"))
        assertFalse(source.contains("scene.acceleration *= animationSpeed;"))
        assertTrue(source.contains("this.scene.__showdownNativeDamageArmed = !move || move.category !== 'Status';"))
        assertTrue(source.contains("this.scene.__showdownNativeDamagePlayed = false;"))
        assertTrue(source.contains("this.scene.__showdownNativeHealthEvents = [];"))
        assertTrue(source.contains("var healthEvent = this.__showdownNativeHealthEvents && this.__showdownNativeHealthEvents.length ? this.__showdownNativeHealthEvents.shift() : null;"))
        assertTrue(source.contains("var shouldCueDamage = healthEvent === 'damage' && this.animating"))
        assertTrue(source.contains("nativeCue('generic_damage');"))
        assertTrue(source.contains("var originalHealAnim = BattleScene.prototype.healAnim;"))
        assertTrue(source.contains("function setHpValue(pokemon, health)"))
        assertTrue(source.contains("this.scene.__showdownNativeHealthEvents.push(kwArgs.from ? 'other' : 'damage');"))
        assertTrue(source.contains("this.scene.__showdownNativeHealthEvents.push('heal');"))
        assertTrue(source.contains("var setHpTarget = this.getPokemon(args[setHpIndex]);"))
        assertTrue(source.contains("var nextHp = setHpValue(setHpTarget, args[setHpIndex + 1]);"))
        assertTrue(source.contains("if (setHpTarget && nextHp !== null) this.scene.__showdownNativeHealthEvents.push(nextHp <= setHpTarget.hp ? 'damage' : 'heal');"))
        assertFalse(source.contains("setHpTarget.healthParse(args[setHpIndex + 1])"))
        assertTrue(source.contains("args[0] === '-clearpositiveboost'"))
        assertTrue(source.contains("args[0] === '-clearnegativeboost'"))
        assertTrue(source.contains("fun battleStarted()"))
        assertTrue(source.contains("resetAudio()"))
        assertTrue(source.contains("fun moveStarted()"))
        assertTrue(source.contains("resetMoveAudio()"))
        assertTrue(source.contains("if (!this.scene.__showdownNativeResultCues) this.scene.__showdownNativeResultCues = [];"))
        assertTrue(source.contains("this.__showdownNativeResultCues && this.__showdownNativeResultCues.length"))
        assertTrue(source.contains("shouldCueResult ? this.__showdownNativeResultCues.shift() : null;"))
        assertTrue(source.contains("this.scene.__showdownNativeResultCues.push(resultCue);"))
        assertFalse(source.contains("__showdownNativeResultCue = null"))
        assertTrue(source.contains("this.scene.__showdownNativeAudioSilent = !!kwArgs.silent;"))
        assertTrue(source.contains("seed(protocolHistoryProvider())"))
        assertTrue(source.contains("flushPendingPackets(allowSeedWhilePaused = true)"))
        assertFalse(source.contains("if (resultCue && this.scene.__showdownNativeResultCue === resultCue)"))
        assertFalse(source.contains("BattlePlaybackTiming.pauseAfter(packet)"))
        assertFalse(source.contains("postDelayed(flushRunnable"))
        assertFalse(source.contains("var originalRunMoveAnim = BattleScene.prototype.runMoveAnim;"))
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
