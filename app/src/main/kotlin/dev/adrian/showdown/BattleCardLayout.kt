package dev.adrian.showdown

data class BattleCardContentLayout(
    val titleBaselineFraction: Float,
    val hpBaselineFraction: Float,
    val barTopFraction: Float,
    val barBottomFraction: Float
)

data class CompactBattleCardLayout(
    val heightFraction: Float,
    val gapFraction: Float,
    val content: BattleCardContentLayout
)

data class BattleCardBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object BattleCardLayout {
    const val PARTY_INDICATOR_MINIMUM_PIXELS = 22f
    const val PARTY_INDICATOR_BOTTOM_INSET_PIXELS = 3f

    private val singleCardContent = BattleCardContentLayout(
        titleBaselineFraction = 0.29f,
        hpBaselineFraction = 0.51f,
        barTopFraction = 0.55f,
        barBottomFraction = 0.70f
    )

    fun compactFor(activeCount: Int) = CompactBattleCardLayout(
        heightFraction = if (activeCount > 2) 0.075f else 0.085f,
        gapFraction = if (activeCount > 2) 0.008f else 0.012f,
        content = singleCardContent
    )

    fun partyIndicatorSize(cardHeight: Float, scale: Float) = maxOf(
        cardHeight * 0.17f,
        PARTY_INDICATOR_MINIMUM_PIXELS * scale
    )

    fun partyIndicatorTop(cardBottom: Float, indicatorSize: Float, scale: Float) =
        cardBottom - indicatorSize - PARTY_INDICATOR_BOTTOM_INSET_PIXELS * scale

    fun compactBoundsFor(width: Float, height: Float, player: Boolean, index: Int, activeCount: Int): BattleCardBounds {
        val layout = compactFor(activeCount)
        val cardLeft = if (player) width * 0.015f else width * 0.685f
        val cardRight = if (player) width * 0.315f else width * 0.985f
        val cardHeight = height * layout.heightFraction
        val cardGap = height * layout.gapFraction
        val totalHeight = cardHeight * activeCount + cardGap * (activeCount - 1)
        val firstTop = if (player) height - totalHeight - height * 0.015f else height * 0.02f
        val top = firstTop + index * (cardHeight + cardGap)
        return BattleCardBounds(cardLeft, top, cardRight, top + cardHeight)
    }
}
