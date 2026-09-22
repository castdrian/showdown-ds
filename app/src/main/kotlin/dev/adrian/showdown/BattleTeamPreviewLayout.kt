data class BattleTeamPreviewSlot(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float
        get() = right - left

    val height: Float
        get() = bottom - top
}

object BattleTeamPreviewLayout {
    private const val MAX_PARTY_SIZE = 6
    private const val COLUMN_COUNT = 3
    private const val HORIZONTAL_MARGIN_FRACTION = 0.075f
    private const val HORIZONTAL_GAP_FRACTION = 0.018f
    private const val TOP_FRACTION = 0.24f
    private const val BOTTOM_FRACTION = 0.89f
    private const val VERTICAL_GAP_FRACTION = 0.028f

    fun slots(width: Float, height: Float, count: Int): List<BattleTeamPreviewSlot> {
        val visibleCount = count.coerceIn(0, MAX_PARTY_SIZE)
        if (visibleCount == 0) return emptyList()
        val columns = minOf(COLUMN_COUNT, visibleCount)
        val rows = (visibleCount + columns - 1) / columns
        val horizontalGap = width * HORIZONTAL_GAP_FRACTION
        val horizontalMargin = width * HORIZONTAL_MARGIN_FRACTION
        val cardWidth = (width - horizontalMargin * 2f - horizontalGap * (columns - 1)) / columns
        val verticalGap = height * VERTICAL_GAP_FRACTION
        val top = height * TOP_FRACTION
        val cardHeight = (height * BOTTOM_FRACTION - top - verticalGap * (rows - 1)) / rows
        return (0 until visibleCount).map { index ->
            val row = index / columns
            val rowCount = minOf(columns, visibleCount - row * columns)
            val rowOffset = (columns - rowCount) * (cardWidth + horizontalGap) / 2f
            val column = index % columns
            val left = horizontalMargin + rowOffset + column * (cardWidth + horizontalGap)
            val rowTop = top + row * (cardHeight + verticalGap)
            BattleTeamPreviewSlot(left, rowTop, left + cardWidth, rowTop + cardHeight)
        }
    }
}
