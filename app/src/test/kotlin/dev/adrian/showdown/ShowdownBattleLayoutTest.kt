package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownBattleLayoutTest {
    @Test
    fun singlesUseShowdownFieldCoordinates() {
        assertEquals(210f, ShowdownBattleLayout.PLAYER_X)
        assertEquals(245f, ShowdownBattleLayout.PLAYER_Y)
        assertEquals(430f, ShowdownBattleLayout.OPPONENT_X)
        assertEquals(135f, ShowdownBattleLayout.OPPONENT_Y)
        assertEquals(1.5f, ShowdownBattleLayout.PLAYER_SCALE)
        assertEquals(1f, ShowdownBattleLayout.OPPONENT_SCALE)
    }

    @Test
    fun designCoordinatesScaleToTheShowdownViewport() {
        assertEquals(630f, ShowdownBattleLayout.x(1920f, ShowdownBattleLayout.PLAYER_X))
        assertEquals(735f, ShowdownBattleLayout.y(1080f, ShowdownBattleLayout.PLAYER_Y))
        assertEquals(1290f, ShowdownBattleLayout.x(1920f, ShowdownBattleLayout.OPPONENT_X))
        assertEquals(405f, ShowdownBattleLayout.y(1080f, ShowdownBattleLayout.OPPONENT_Y))
    }

    @Test
    fun playerCardStopsBeforeTheSinglesSpriteBounds() {
        val spriteLeft = ShowdownBattleLayout.x(1920f, ShowdownBattleLayout.PLAYER_X) -
            ShowdownBattleLayout.BASE_SPRITE_WIDTH * ShowdownBattleLayout.PLAYER_SCALE / 2f

        assertTrue(ShowdownBattleLayout.singlePlayerCardRight(1920f, 1f) < spriteLeft)
    }

    @Test
    fun singlesUseOneSharedHpCardWidthOnBothSides() {
        val width = 1920f
        val scale = 1f
        val playerLeft = width * ShowdownBattleLayout.SINGLE_CARD_LEFT_FRACTION
        val playerRight = ShowdownBattleLayout.singlePlayerCardRight(width, scale)
        val opponentLeft = ShowdownBattleLayout.singleOpponentCardLeft(width, scale)
        val opponentRight = width * ShowdownBattleLayout.SINGLE_CARD_RIGHT_FRACTION

        assertEquals(playerRight - playerLeft, opponentRight - opponentLeft, 0.001f)
        assertEquals(381.7f, opponentRight - opponentLeft, 0.1f)
    }
}
