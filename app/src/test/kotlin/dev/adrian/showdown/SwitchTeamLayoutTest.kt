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

    @Test
    fun switchTeamContentStaysInsideEachCardAtThorLowerDensity() {
        val card = SwitchTeamLayout.bounds(1240f, 1080f, 1f, 5, 6)
        val content = SwitchTeamLayout.contentBounds(card, 1f, false)

        assertTrue(content.sprite.left >= card.left)
        assertTrue(content.sprite.right <= card.right)
        assertTrue(content.sprite.top >= card.top)
        assertTrue(content.sprite.bottom <= content.bottomRow.top)
        assertTrue(content.header.left >= card.left)
        assertTrue(content.header.right <= card.right)
        assertTrue(content.header.bottom <= content.hp.top)
        assertTrue(content.hp.left >= card.left)
        assertTrue(content.hp.right <= card.right)
        assertTrue(content.hp.bottom <= content.bottomRow.top)
        assertTrue(content.bottomRow.bottom <= card.bottom)
    }

    @Test
    fun switchTeamContentKeepsAVisibleGapBetweenEveryVerticalRegion() {
        (1..6).forEach { teamSize ->
            (0 until teamSize).forEach { index ->
                val card = SwitchTeamLayout.bounds(1240f, 1080f, 1f, index, teamSize)
                val content = SwitchTeamLayout.contentBounds(card, 1f, false)

                assertTrue(content.hp.top - content.header.bottom >= SwitchTeamLayout.CONTENT_GAP)
                assertTrue(content.bottomRow.top - content.hp.bottom >= SwitchTeamLayout.CONTENT_GAP)
                assertTrue(content.bottomRow.top - content.sprite.bottom >= SwitchTeamLayout.CONTENT_GAP)
            }
        }
    }

    @Test
    fun switchTeamContentRemainsSeparatedWhenThePresentationHasLessHeight() {
        val card = SwitchTeamLayout.bounds(1240f, 900f, 1f, 5, 6)
        val content = SwitchTeamLayout.contentBounds(card, 1f, false)

        assertTrue(content.sprite.bottom <= content.bottomRow.top)
        assertTrue(content.header.bottom <= content.hp.top)
        assertTrue(content.hp.bottom <= content.bottomRow.top)
        assertTrue(content.bottomRow.bottom <= card.bottom)
    }

    @Test
    fun teamPreviewHeaderLeavesRoomForItsOrderMarker() {
        val card = SwitchTeamLayout.bounds(1240f, 1080f, 1f, 0, 6)
        val content = SwitchTeamLayout.contentBounds(card, 1f, true)
        val markerLeft = card.right - 90f

        assertTrue(content.header.right < markerLeft)
        assertTrue(content.header.bottom <= content.hp.top)
    }

    @Test
    fun switchTeamContentNeverReclaimsSpaceBelowTheCardOnAShortSurface() {
        val card = SwitchTeamLayout.bounds(1240f, 600f, 1f, 5, 6)
        val content = SwitchTeamLayout.contentBounds(card, 1f, false)

        assertTrue(content.header.bottom <= content.hp.top)
        assertTrue(content.hp.bottom <= content.bottomRow.top)
        assertTrue(content.sprite.bottom <= content.bottomRow.top)
        assertTrue(content.bottomRow.bottom <= card.bottom)
    }

    @Test
    fun switchTeamCardsRemainOrderedWhenTheAvailableSurfaceIsVeryShort() {
        val first = SwitchTeamLayout.bounds(1240f, 240f, 1f, 0, 6)
        val last = SwitchTeamLayout.bounds(1240f, 240f, 1f, 5, 6)

        assertTrue(first.top >= 0f)
        assertTrue(first.bottom >= first.top)
        assertTrue(last.top >= first.bottom)
        assertTrue(last.bottom >= last.top)
    }

    @Test
    fun compressedRowsNeverCreateInvertedRegions() {
        val card = SwitchTeamLayout.bounds(420f, 240f, 0.34f, 0, 6)
        val content = SwitchTeamLayout.contentBounds(card, 0.34f, true)
        val row = SwitchTeamLayout.rowBounds(card, 0.34f, 2)

        assertTrue(row.left <= row.typeRight)
        assertTrue(row.typeRight <= row.statusLeft)
        assertTrue(row.statusLeft <= row.right)
        assertTrue(row.typeWidth >= 0f)
        assertTrue(content.header.left <= content.header.right)
        assertTrue(content.header.top <= content.header.bottom)
        assertTrue(content.hp.left <= content.hp.right)
        assertTrue(content.hp.top <= content.hp.bottom)
        assertTrue(content.bottomRow.left <= content.bottomRow.right)
        assertTrue(content.bottomRow.top <= content.bottomRow.bottom)
        assertTrue(content.sprite.left <= content.sprite.right)
        assertTrue(content.sprite.top <= content.sprite.bottom)
    }
}
