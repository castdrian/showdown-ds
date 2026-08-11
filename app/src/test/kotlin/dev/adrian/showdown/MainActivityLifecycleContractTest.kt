package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class MainActivityLifecycleContractTest {
    @Test
    fun liveEffectsPauseAndResumeWithTheActivityLifecycle() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()
        val pauseIndex = source.indexOf("pauseLivePlaybackForLifecycle()")
        val resumeIndex = source.indexOf("resumeLivePlaybackForLifecycle()")

        assertTrue(pauseIndex >= 0)
        assertTrue(resumeIndex > pauseIndex)
        assertTrue(source.contains("livePlaybackPausedForLifecycle = true"))
        assertTrue(source.contains("livePlaybackPausedForLifecycle = false"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(true)"))
        assertTrue(source.contains("showdownMoveEffects?.setPlaybackPaused(false)"))
    }
}
