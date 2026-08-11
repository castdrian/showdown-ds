package dev.adrian.showdown

import java.io.File
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
}
