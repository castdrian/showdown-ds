package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleTeamPreviewLayoutTest {
    @Test
    fun fullTeamUsesTwoReadableRowsWithoutOverlap() {
        val slots = BattleTeamPreviewLayout.slots(1920f, 1080f, 6)

        assertEquals(6, slots.size)
        assertTrue(slots.all { it.width > 400f })
        assertTrue(slots.all { it.height > 250f })
        assertTrue(slots.zipWithNext().all { (first, second) -> first.right <= second.left || first.bottom <= second.top })
        assertTrue(slots.last().bottom <= 1080f)
    }

    @Test
    fun partialLastRowStaysCentered() {
        val slots = BattleTeamPreviewLayout.slots(1920f, 1080f, 5)

        assertEquals(5, slots.size)
        assertEquals(slots[0].left, slots[3].left - (slots[1].left - slots[0].left) / 2f, 0.01f)
        assertTrue(slots[3].left > slots[0].left)
        assertTrue(slots[4].right < slots[2].right)
    }
}
