package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattlePlaybackSpeedTest {
    @Test
    fun restrictsTheVisualTimelineToSoundPoolRates() {
        assertEquals(BattlePlaybackSpeed.MINIMUM, BattlePlaybackSpeed.coerce(0.25f))
        assertEquals(BattlePlaybackSpeed.MAXIMUM, BattlePlaybackSpeed.coerce(4f))
        assertEquals(0.75f, BattlePlaybackSpeed.coerce(0.75f))
        assertEquals(1f, BattlePlaybackSpeed.coerce(Float.NaN))
    }
}
