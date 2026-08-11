package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecycleContractTest {
    @Test
    fun liveEffectsPauseAndResumeWithTheActivityLifecycle() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val pauseIndex = source.indexOf("pauseLiveEffectsForLifecycle()")
        val resumeIndex = source.indexOf("resumeLiveEffectsForLifecycle()")

        assertTrue(pauseIndex >= 0)
        assertTrue(resumeIndex > pauseIndex)
        assertTrue(source.contains("liveEffectsPausedForLifecycle = true"))
        assertTrue(source.contains("liveEffectsPausedForLifecycle = false"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(true)"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(false)"))
    }
}
