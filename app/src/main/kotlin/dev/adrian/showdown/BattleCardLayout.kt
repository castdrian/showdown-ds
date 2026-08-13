package dev.adrian.showdown

data class CompactBattleCardLayout(
    val heightFraction: Float,
    val gapFraction: Float,
    val titleBaselineFraction: Float,
    val hpBaselineFraction: Float,
    val barTopFraction: Float,
    val barBottomFraction: Float
)

data class BattleCardBounds(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
)

object BattleCardLayout {
    fun compactFor(activeCount: Int) = CompactBattleCardLayout(
        heightFraction = if (activeCount > 2) 0.075f else 0.085f,
        gapFraction = if (activeCount > 2) 0.008f else 0.012f,
        titleBaselineFraction = 0.29f,
        hpBaselineFraction = 0.51f,
        barTopFraction = 0.55f,
        barBottomFraction = 0.70f
    )

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
