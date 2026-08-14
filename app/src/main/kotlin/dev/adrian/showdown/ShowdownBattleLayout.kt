package dev.adrian.showdown

object ShowdownBattleLayout {
    const val DESIGN_WIDTH = 640f
    const val DESIGN_HEIGHT = 360f
    const val PLAYER_X = 210f
    const val PLAYER_Y = 245f
    const val OPPONENT_X = 430f
    const val OPPONENT_Y = 135f
    const val PLAYER_SCALE = 1.5f
    const val OPPONENT_SCALE = 1f
    const val BASE_SPRITE_WIDTH = 290f
    const val CARD_SPRITE_GAP = 2f
    const val SINGLE_CARD_LEFT_FRACTION = 0.015f
    const val SINGLE_CARD_RIGHT_FRACTION = 0.985f

    fun x(width: Float, designX: Float) = width * designX / DESIGN_WIDTH

    fun y(height: Float, designY: Float) = height * designY / DESIGN_HEIGHT

    fun singlePlayerCardRight(width: Float, scale: Float) =
        x(width, PLAYER_X) - BASE_SPRITE_WIDTH * scale * PLAYER_SCALE / 2f - CARD_SPRITE_GAP * scale

    fun singleStatusCardWidth(width: Float, scale: Float) =
        singlePlayerCardRight(width, scale) - width * SINGLE_CARD_LEFT_FRACTION

    fun singleOpponentCardLeft(width: Float, scale: Float) =
        width * SINGLE_CARD_RIGHT_FRACTION - singleStatusCardWidth(width, scale)
}
