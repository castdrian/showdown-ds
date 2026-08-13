package dev.adrian.showdown

object BattleTargetLayout {
    const val MAX_OPTIONS = 6
    private const val MAX_COLUMNS = 3
    private const val HEADER_HEIGHT = 28f
    private const val OPTION_HEIGHT = 54f
    private const val ROW_GAP = 10f
    private const val SECTION_BOTTOM_PADDING = 30f

    fun columnsFor(optionCount: Int): Int = optionCount.coerceIn(1, MAX_OPTIONS).coerceAtMost(MAX_COLUMNS)

    fun rowsFor(optionCount: Int): Int {
        val count = optionCount.coerceIn(0, MAX_OPTIONS)
        if (count == 0) return 0
        return (count + columnsFor(count) - 1) / columnsFor(count)
    }

    fun sectionHeight(optionCount: Int, scale: Float): Float {
        val rows = rowsFor(optionCount)
        if (rows == 0) return 0f
        return (HEADER_HEIGHT + OPTION_HEIGHT * rows + ROW_GAP * (rows - 1) + SECTION_BOTTOM_PADDING) * scale
    }

    fun optionTop(index: Int, optionCount: Int, top: Float, scale: Float): Float {
        val columns = columnsFor(optionCount)
        return top + HEADER_HEIGHT * scale + (index / columns) * (OPTION_HEIGHT + ROW_GAP) * scale
    }

    fun optionHeight(scale: Float): Float = OPTION_HEIGHT * scale

    fun optionWidth(totalWidth: Float, optionCount: Int, scale: Float): Float {
        val columns = columnsFor(optionCount)
        val gap = 16f * scale
        return (totalWidth - gap * (columns - 1)) / columns
    }

    fun optionLeft(left: Float, index: Int, optionCount: Int, totalWidth: Float, scale: Float): Float {
        val columns = columnsFor(optionCount)
        val gap = 16f * scale
        val width = optionWidth(totalWidth, optionCount, scale)
        return left + (index % columns) * (width + gap)
    }
}
