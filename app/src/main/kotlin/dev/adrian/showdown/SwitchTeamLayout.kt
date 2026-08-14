package dev.adrian.showdown

data class SwitchTeamCardBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object SwitchTeamLayout {
    const val COLUMNS = 2
    const val LEFT = 44f
    const val TOP = 220f
    const val GAP = 14f
    const val BOTTOM_MARGIN = 28f

    fun rows(teamSize: Int) = (teamSize + COLUMNS - 1) / COLUMNS

    fun bounds(width: Float, height: Float, scale: Float, index: Int, teamSize: Int): SwitchTeamCardBounds {
        val left = LEFT * scale
        val top = TOP * scale
        val gap = GAP * scale
        val rows = rows(teamSize)
        val cardWidth = (width - left * 2f - gap) / COLUMNS
        val cardHeight = (height - top - BOTTOM_MARGIN * scale - gap * (rows - 1)) / rows
        val row = index / COLUMNS
        val column = index % COLUMNS
        val cardLeft = left + column * (cardWidth + gap)
        val cardTop = top + row * (cardHeight + gap)
        return SwitchTeamCardBounds(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
    }
}
