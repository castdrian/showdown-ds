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
    fun compactFor(activeCount: Int): CompactBattleCardLayout = if (activeCount > 2) {
        CompactBattleCardLayout(
            heightFraction = 0.075f,
            gapFraction = 0.008f,
            titleBaselineFraction = 0.36f,
            hpBaselineFraction = 0.62f,
            barTopFraction = 0.70f,
            barBottomFraction = 0.86f
        )
    } else {
        CompactBattleCardLayout(
            heightFraction = 0.085f,
            gapFraction = 0.012f,
            titleBaselineFraction = 0.29f,
            hpBaselineFraction = 0.51f,
            barTopFraction = 0.55f,
            barBottomFraction = 0.70f
        )
    }
}
