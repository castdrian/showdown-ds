package dev.adrian.showdown

object TeamRosterPager {
    const val PAGE_SIZE = 6

    fun pageCount(teamSize: Int): Int = (teamSize.coerceAtLeast(0) + PAGE_SIZE - 1) / PAGE_SIZE

    fun visibleIndices(teamSize: Int, pageIndex: Int): IntRange {
        if (teamSize <= 0) return IntRange.EMPTY
        val page = pageIndex.coerceIn(0, pageCount(teamSize) - 1)
        val first = page * PAGE_SIZE
        return first until minOf(first + PAGE_SIZE, teamSize)
    }

    fun pageContaining(teamSize: Int, teamIndex: Int): Int {
        if (teamSize <= 0) return 0
        return teamIndex.coerceIn(0, teamSize - 1) / PAGE_SIZE
    }

    fun pageKeepingFocusedMemberVisible(pageIndex: Int, teamSize: Int, teamIndex: Int): Int {
        if (teamSize <= 0) return 0
        val visibleIndices = visibleIndices(teamSize, pageIndex)
        return if (teamIndex in visibleIndices) {
            pageIndex.coerceIn(0, pageCount(teamSize) - 1)
        } else {
            pageContaining(teamSize, teamIndex)
        }
    }

    fun movePage(pageIndex: Int, teamSize: Int, direction: Int): Int {
        val lastPage = pageCount(teamSize) - 1
        if (lastPage < 0) return 0
        return (pageIndex + direction).coerceIn(0, lastPage)
    }
}
