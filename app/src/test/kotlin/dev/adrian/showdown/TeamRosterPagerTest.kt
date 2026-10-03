package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamRosterPagerTest {
    @Test
    fun twentyFourMemberTeamPagesIntoFourVisibleGroups() {
        assertEquals(4, TeamRosterPager.pageCount(24))
        assertEquals(0..5, TeamRosterPager.visibleIndices(24, 0))
        assertEquals(6..11, TeamRosterPager.visibleIndices(24, 1))
        assertEquals(18..23, TeamRosterPager.visibleIndices(24, 3))
    }

    @Test
    fun focusedMemberMovesTheVisiblePageOnlyWhenOutsideCurrentPage() {
        assertEquals(2, TeamRosterPager.pageContaining(24, 14))
        assertEquals(2, TeamRosterPager.pageKeepingFocusedMemberVisible(2, 24, 14))
        assertEquals(2, TeamRosterPager.pageKeepingFocusedMemberVisible(1, 24, 14))
    }

    @Test
    fun pageNavigationStaysWithinTheRoster() {
        assertEquals(0, TeamRosterPager.movePage(0, 24, -1))
        assertEquals(1, TeamRosterPager.movePage(0, 24, 1))
        assertEquals(3, TeamRosterPager.movePage(8, 24, 1))
        assertEquals(0, TeamRosterPager.pageCount(0))
        assertEquals(IntRange.EMPTY, TeamRosterPager.visibleIndices(0, 0))
    }
}
