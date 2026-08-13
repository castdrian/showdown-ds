package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleTargetLayoutTest {
    @Test
    fun fiveTargetsUseTwoRowsAndKeepSixTouchSlotsAvailable() {
        assertEquals(3, BattleTargetLayout.columnsFor(5))
        assertEquals(2, BattleTargetLayout.rowsFor(5))
        assertEquals(6, BattleTargetLayout.MAX_OPTIONS)
        assertEquals(176f, BattleTargetLayout.sectionHeight(5, 1f), 0.001f)
    }

    @Test
    fun threeTargetsStayOnOneCompactRow() {
        assertEquals(3, BattleTargetLayout.columnsFor(3))
        assertEquals(1, BattleTargetLayout.rowsFor(3))
        assertEquals(112f, BattleTargetLayout.sectionHeight(3, 1f), 0.001f)
    }
}
