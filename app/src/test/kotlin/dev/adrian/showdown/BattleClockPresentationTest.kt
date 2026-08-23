package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleClockPresentationTest {
    @Test
    fun formatsBattleTimeAsMinutesAndSeconds() {
        assertEquals("0:00", BattleClockPresentation.timeLabel(0))
        assertEquals("2:05", BattleClockPresentation.timeLabel(125))
        assertEquals("0:00", BattleClockPresentation.timeLabel(-10))
    }

    @Test
    fun escalatesClockUrgencyAsTimeRunsOut() {
        assertEquals(BattleClockUrgency.NORMAL, BattleClockPresentation.urgency(31))
        assertEquals(BattleClockUrgency.WARNING, BattleClockPresentation.urgency(30))
        assertEquals(BattleClockUrgency.CRITICAL, BattleClockPresentation.urgency(10))
    }
}
