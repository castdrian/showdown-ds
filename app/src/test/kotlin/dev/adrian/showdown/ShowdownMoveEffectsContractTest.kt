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
        assertTrue(source.contains("this.__showdownNativeResultCue = null;"))
        assertTrue(source.contains("this.__showdownNativeResultCue = null;\n                                    nativeCue(cue);"))
        assertFalse(source.contains("if (resultCue && this.scene.__showdownNativeResultCue === resultCue)"))
        assertFalse(source.contains("BattlePlaybackTiming.pauseAfter(packet)"))
        assertFalse(source.contains("postDelayed(flushRunnable"))
    }
}
