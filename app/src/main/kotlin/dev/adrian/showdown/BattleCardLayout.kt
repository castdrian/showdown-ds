package dev.adrian.showdown

data class CompactBattleCardLayout(
    val heightFraction: Float,
    val gapFraction: Float,
    val titleBaselineFraction: Float,
    val hpBaselineFraction: Float,
    val barTopFraction: Float,
    val barBottomFraction: Float
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
}
