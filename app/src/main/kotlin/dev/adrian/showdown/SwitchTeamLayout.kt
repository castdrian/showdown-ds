package dev.adrian.showdown

data class SwitchTeamCardBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

data class SwitchTeamRowBounds(
    val left: Float,
    val typeRight: Float,
    val statusLeft: Float,
    val right: Float,
    val typeGap: Float,
    val typeWidth: Float
)

object SwitchTeamLayout {
    const val COLUMNS = 2
    const val LEFT = 44f
    const val TOP = 220f
    const val GAP = 14f
    const val BOTTOM_MARGIN = 28f
    const val CARD_BOTTOM_ROW_LEFT = 18f
    const val CARD_BOTTOM_ROW_RIGHT = 18f
    const val STATUS_WIDTH = 204f
    const val STATUS_GAP = 10f
    const val TYPE_GAP = 8f

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

    fun rowBounds(card: SwitchTeamCardBounds, scale: Float, typeCount: Int): SwitchTeamRowBounds {
        val left = card.left + CARD_BOTTOM_ROW_LEFT * scale
        val right = card.right - CARD_BOTTOM_ROW_RIGHT * scale
        val statusWidth = minOf(STATUS_WIDTH * scale, (right - left) * 0.42f)
        val statusLeft = right - statusWidth
        val typeRight = statusLeft - STATUS_GAP * scale
        val count = typeCount.coerceAtLeast(1)
        val typeGap = TYPE_GAP * scale
        val typeWidth = ((typeRight - left) - typeGap * (count - 1)) / count
        return SwitchTeamRowBounds(left, typeRight, statusLeft, right, typeGap, typeWidth)
    }

    fun typeBounds(row: SwitchTeamRowBounds, index: Int): SwitchTeamCardBounds {
        val left = row.left + index * (row.typeWidth + row.typeGap)
        return SwitchTeamCardBounds(left, 0f, left + row.typeWidth, 0f)
    }
}
