package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleAudioDiagnosticTest {
    @Test
    fun passesOnlyWhenEveryCueLoadsAndStarts() {
        val cues = BattleAudioCue.values().toSet()
        val events = BattleAudioCue.values().map { cue ->
            BattleAudioCueEvent(cue, 100L, 0L, 3L, true)
        }

        assertTrue(BattleAudioDiagnosticSnapshot(cues, emptySet(), events).passed)
        assertFalse(BattleAudioDiagnosticSnapshot(cues - BattleAudioCue.STAT_DROP, emptySet(), events).passed)
        assertFalse(BattleAudioDiagnosticSnapshot(cues, emptySet(), events.dropLast(1)).passed)
        assertFalse(BattleAudioDiagnosticSnapshot(cues, emptySet(), events.dropLast(1) + events.last().copy(playbackAccepted = false)).passed)
    }
}
