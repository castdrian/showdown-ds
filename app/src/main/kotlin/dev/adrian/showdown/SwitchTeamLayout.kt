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

data class SwitchTeamCardContentBounds(
    val sprite: SwitchTeamCardBounds,
    val header: SwitchTeamCardBounds,
    val hp: SwitchTeamCardBounds,
    val bottomRow: SwitchTeamCardBounds
)

object SwitchTeamLayout {
    const val COLUMNS = 2
    const val LEFT = 44f
    const val TOP = 220f
    const val DECISION_PROMPT_TOP = 112f
    const val DECISION_PROMPT_BOTTOM = 160f
    const val DECISION_TOP = 180f
    const val GAP = 14f
    const val BOTTOM_MARGIN = 28f
    const val STATUS_WIDTH = 204f
    const val STATUS_GAP = 10f
    const val TYPE_GAP = 8f
    const val CARD_INSET = 18f
    const val CARD_HEADER_LEFT_OFFSET = 164f
    const val CARD_HEADER_TOP_OFFSET = 18f
    const val CONTENT_GAP = 12f
    const val PREVIEW_MARKER_RESERVED_WIDTH = 110f
    const val CARD_BOTTOM_ROW_HEIGHT = 52f
    const val CARD_HP_HEIGHT = 44f
    const val CARD_SPRITE_SIZE = 134f

    fun rows(teamSize: Int) = (teamSize + COLUMNS - 1) / COLUMNS

    fun bounds(width: Float, height: Float, scale: Float, index: Int, teamSize: Int): SwitchTeamCardBounds =
        boundsForTop(width, height, scale, index, teamSize, TOP)

    fun decisionBounds(width: Float, height: Float, scale: Float, index: Int, teamSize: Int): SwitchTeamCardBounds =
        boundsForTop(width, height, scale, index, teamSize, DECISION_TOP)

    private fun boundsForTop(
        width: Float,
        height: Float,
        scale: Float,
        index: Int,
        teamSize: Int,
        top: Float
    ): SwitchTeamCardBounds {
        val left = LEFT * scale
        val topPosition = top * scale
        val gap = GAP * scale
        val rowCount = rows(teamSize).coerceAtLeast(1)
        val cardWidth = (width - left * 2f - gap) / COLUMNS
        val availableHeight = (height - topPosition - BOTTOM_MARGIN * scale - gap * (rowCount - 1)).coerceAtLeast(0f)
        val cardHeight = availableHeight / rowCount
        val row = index / COLUMNS
        val column = index % COLUMNS
        val cardLeft = left + column * (cardWidth + gap)
        val cardTop = topPosition + row * (cardHeight + gap)
        return SwitchTeamCardBounds(cardLeft, cardTop, cardLeft + cardWidth, cardTop + cardHeight)
    }

    fun rowBounds(card: SwitchTeamCardBounds, scale: Float, typeCount: Int): SwitchTeamRowBounds {
        val inset = CARD_INSET * scale
        val left = minOf(card.left + inset, card.right)
        val right = maxOf(left, card.right - inset)
        val availableWidth = (right - left).coerceAtLeast(0f)
        val statusWidth = minOf(STATUS_WIDTH * scale, availableWidth * 0.42f)
        val statusLeft = (right - statusWidth).coerceIn(left, right)
        val typeRight = (statusLeft - STATUS_GAP * scale).coerceAtLeast(left)
        val count = typeCount.coerceAtLeast(1)
        val typeGap = TYPE_GAP * scale
        val typeWidth = (((typeRight - left) - typeGap * (count - 1)).coerceAtLeast(0f)) / count
        return SwitchTeamRowBounds(left, typeRight, statusLeft, right, typeGap, typeWidth)
    }

    fun typeBounds(row: SwitchTeamRowBounds, index: Int): SwitchTeamCardBounds {
        val left = row.left + index * (row.typeWidth + row.typeGap)
        return SwitchTeamCardBounds(left, 0f, left + row.typeWidth, 0f)
    }

    fun contentBounds(card: SwitchTeamCardBounds, scale: Float, reservesPreviewMarker: Boolean): SwitchTeamCardContentBounds {
        val inset = CARD_INSET * scale
        val contentTop = card.top + inset
        val contentBottom = (card.bottom - inset).coerceAtLeast(contentTop)
        val availableHeight = (contentBottom - contentTop).coerceAtLeast(0f)
        val gap = CONTENT_GAP * scale
        val bottomRowHeight = minOf(
            CARD_BOTTOM_ROW_HEIGHT * scale,
            maxOf(32f * scale, availableHeight * 0.24f),
            availableHeight
        )
        val bottomRow = SwitchTeamCardBounds(
            card.left + inset,
            (contentBottom - bottomRowHeight).coerceAtLeast(contentTop),
            card.right - inset,
            contentBottom
        )
        val hpBottom = (bottomRow.top - gap).coerceAtLeast(contentTop)
        val hpHeight = minOf(
            CARD_HP_HEIGHT * scale,
            (hpBottom - contentTop).coerceAtLeast(0f)
        )
        val hp = SwitchTeamCardBounds(
            minOf(card.left + CARD_HEADER_LEFT_OFFSET * scale, card.right - inset),
            (hpBottom - hpHeight).coerceAtLeast(contentTop),
            card.right - inset,
            hpBottom
        )
        val headerTop = (card.top + CARD_HEADER_TOP_OFFSET * scale).coerceAtMost(hp.top)
        val headerBottom = (hp.top - gap).coerceAtLeast(headerTop)
        val headerLeft = minOf(card.left + CARD_HEADER_LEFT_OFFSET * scale, card.right - inset)
        val headerRight = minOf(
            if (reservesPreviewMarker) card.right - PREVIEW_MARKER_RESERVED_WIDTH * scale else card.right - inset,
            card.right - inset
        )
        val header = SwitchTeamCardBounds(
            headerLeft,
            headerTop,
            maxOf(headerLeft, headerRight),
            headerBottom
        )
        val spriteLeft = card.left + inset
        val spriteRight = (headerLeft - gap).coerceAtLeast(spriteLeft)
        val spriteTop = contentTop
        val spriteBottom = (bottomRow.top - gap).coerceAtLeast(spriteTop)
        val spriteSize = minOf(
            CARD_SPRITE_SIZE * scale,
            (spriteRight - spriteLeft).coerceAtLeast(0f),
            (spriteBottom - spriteTop).coerceAtLeast(0f)
        )
        val spriteCenterX = (spriteLeft + spriteRight) / 2f
        val spriteCenterY = (spriteTop + spriteBottom) / 2f
        val sprite = SwitchTeamCardBounds(
            spriteCenterX - spriteSize / 2f,
            spriteCenterY - spriteSize / 2f,
            spriteCenterX + spriteSize / 2f,
            spriteCenterY + spriteSize / 2f
        )
        return SwitchTeamCardContentBounds(sprite, header, hp, bottomRow)
    }
}
