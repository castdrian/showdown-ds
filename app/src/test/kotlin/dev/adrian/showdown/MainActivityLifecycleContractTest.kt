package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecycleContractTest {
    @Test
    fun liveEffectsPauseAndResumeWithTheActivityLifecycle() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val audioSource = File("src/main/kotlin/dev/adrian/showdown/BattleAudio.kt").readText()
        val pauseIndex = source.indexOf("pauseLivePlaybackForLifecycle()")
        val resumeIndex = source.indexOf("resumeLivePlaybackForLifecycle()")

        assertTrue(pauseIndex >= 0)
        assertTrue(resumeIndex > pauseIndex)
        assertTrue(source.contains("livePlaybackPausedForLifecycle = true"))
        assertTrue(source.contains("livePlaybackPausedForLifecycle = false"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(true)"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(false)"))
        assertTrue(source.contains("displayRefreshScheduler.cancel()"))
        assertTrue(source.contains("battleAudio.pauseBattleCues()"))
        assertTrue(source.contains("battleAudio.resumeBattleCues()"))
        assertTrue(audioSource.contains("activeBattleStreamIds.forEach(battleSoundPool::pause)"))
        assertTrue(audioSource.contains("activeBattleStreamIds.forEach(battleSoundPool::resume)"))
    }

    @Test
    fun resumeRestoresTheThorPresentationIfAndroidDismissedIt() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val resume = source.substringAfter("override fun onResume() {").substringBefore("override fun onWindowFocusChanged")

        assertTrue(resume.contains("showSecondaryDisplay()"))
        assertTrue(source.contains("secondaryPresentation?.isShowing == false"))
    }

    @Test
    fun defersTheAnimationWebViewUntilBattlePlaybackStarts() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val screenFactory = source.substringAfter("private fun createPrimaryScreen()").substringBefore("private fun ensureShowdownMoveEffects()")

        assertTrue(screenFactory.contains("primaryFrame = it"))
        assertTrue(source.contains("private fun ensureShowdownMoveEffects(): ShowdownMoveEffectsView?"))
        assertTrue(source.contains("if (lines.any { it.startsWith(\"|init|battle\") }) ensureShowdownMoveEffects()"))
        assertTrue(source.contains("frame.addView(effects, FrameLayout.LayoutParams(-1, -1))"))
        assertTrue(screenFactory.contains("frame.addView(battleScene, FrameLayout.LayoutParams(-1, -1))"))
        assertFalse(screenFactory.contains("ShowdownMoveEffectsView("))
    }

    @Test
    fun doesNotFeedInitialBattlePacketToFreshlySeededEffectsViewTwice() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val listener = source.substringAfter("private val protocolListener").substringBefore("private val decisionListener")

        assertTrue(listener.contains("runOnUiThread { applyBattleProtocolToEffects(lines) }"))
        assertTrue(source.contains("val effectsAlreadyCreated = showdownMoveEffects != null"))
        assertTrue(source.contains("if (!effectsAlreadyCreated && lines.any { it.startsWith(\"|init|battle\") }) return"))
    }

    @Test
    fun keepsLiveChoicesUntilShowdownAcknowledgesThem() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("pendingDecisionCommand = command"))
        assertTrue(source.contains("pendingDecisionSentConnection = connection"))
        assertTrue(source.contains("pendingDecisionCommand = savedInstanceState?.getString(\"pending_decision_command\")"))
        assertTrue(source.contains("preferences.getString(\"pending_decision_command\", null)"))
        assertTrue(source.contains("reconcilePendingDecisionCommand(lines)"))
        assertFalse(source.contains("if (packet.connection.send(roomId, command)) pendingDecisionCommand = null"))
    }
}
