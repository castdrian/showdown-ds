package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SwitchTeamLayoutTest {
    @Test
    fun switchTeamUsesTwoReadableColumns() {
        assertEquals(2, SwitchTeamLayout.COLUMNS)
        assertEquals(3, SwitchTeamLayout.rows(6))

        val first = SwitchTeamLayout.bounds(1240f, 1080f, 1f, 0, 6)
        val second = SwitchTeamLayout.bounds(1240f, 1080f, 1f, 1, 6)
        val last = SwitchTeamLayout.bounds(1240f, 1080f, 1f, 5, 6)

        assertEquals(first.right - first.left, second.right - second.left, 0.001f)
        assertTrue(second.left > first.right)
        assertTrue(last.bottom <= 1080f - SwitchTeamLayout.BOTTOM_MARGIN)
    }

    @Test
    fun switchTeamBottomRowReservesStatusBeforeSizingTypeBadges() {
        val card = SwitchTeamLayout.bounds(1240f, 1080f, 1f, 0, 6)
        val row = SwitchTeamLayout.rowBounds(card, 1f, 2)
        val firstType = SwitchTeamLayout.typeBounds(row, 0)
        val secondType = SwitchTeamLayout.typeBounds(row, 1)

        assertTrue(firstType.right <= secondType.left)
        assertTrue(secondType.right <= row.typeRight)
        assertTrue(row.typeRight < row.statusLeft)
        assertTrue(row.statusLeft < row.right)
    }
}
