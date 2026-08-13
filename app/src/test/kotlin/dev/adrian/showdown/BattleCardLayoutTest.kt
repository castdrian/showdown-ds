package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCardLayoutTest {
    @Test
    fun triplesKeepReadableContentInsideTheirCompactBounds() {
        val layout = BattleCardLayout.compactFor(3)

        assertTrue(layout.heightFraction > 0.06f)
        assertTrue(layout.content.titleBaselineFraction > 0.27f)
        assertTrue(layout.content.hpBaselineFraction < layout.content.barTopFraction)
        assertTrue(layout.content.barBottomFraction < 1f)
    }

    @Test
    fun singleAndDoubleCardsKeepTheirEstablishedGeometry() {
        val layout = BattleCardLayout.compactFor(2)

        assertEquals(0.085f, layout.heightFraction, 0.001f)
        assertEquals(0.012f, layout.gapFraction, 0.001f)
        assertEquals(0.29f, layout.content.titleBaselineFraction, 0.001f)
        assertEquals(0.51f, layout.content.hpBaselineFraction, 0.001f)
        assertEquals(0.55f, layout.content.barTopFraction, 0.001f)
        assertEquals(0.70f, layout.content.barBottomFraction, 0.001f)
    }

    @Test
    fun everyBattleFormatUsesTheSameCardContentTreatment() {
        val single = BattleCardLayout.compactFor(1)
        val doubles = BattleCardLayout.compactFor(2)
        val triples = BattleCardLayout.compactFor(3)

        assertEquals(single.content, doubles.content)
        assertEquals(single.content, triples.content)
        assertEquals(single.content.titleBaselineFraction, doubles.content.titleBaselineFraction, 0.001f)
        assertEquals(single.content.titleBaselineFraction, triples.content.titleBaselineFraction, 0.001f)
        assertEquals(single.content.hpBaselineFraction, doubles.content.hpBaselineFraction, 0.001f)
        assertEquals(single.content.hpBaselineFraction, triples.content.hpBaselineFraction, 0.001f)
        assertEquals(single.content.barTopFraction, doubles.content.barTopFraction, 0.001f)
        assertEquals(single.content.barTopFraction, triples.content.barTopFraction, 0.001f)
        assertEquals(single.content.barBottomFraction, doubles.content.barBottomFraction, 0.001f)
        assertEquals(single.content.barBottomFraction, triples.content.barBottomFraction, 0.001f)
        assertTrue(triples.heightFraction < doubles.heightFraction)
        assertTrue(triples.gapFraction < doubles.gapFraction)
    }

    @Test
    fun doublesKeepTheSingleCardTreatmentOnBothSides() {
        val playerCards = (0..1).map { index ->
            BattleCardLayout.compactBoundsFor(1920f, 1080f, true, index, 2)
        }
        val opponentCards = (0..1).map { index ->
            BattleCardLayout.compactBoundsFor(1920f, 1080f, false, index, 2)
        }

        assertEquals(playerCards[0].left, playerCards[1].left, 0.001f)
        assertEquals(playerCards[0].right, playerCards[1].right, 0.001f)
        assertEquals(opponentCards[0].left, opponentCards[1].left, 0.001f)
        assertEquals(opponentCards[0].right, opponentCards[1].right, 0.001f)
        assertEquals(playerCards[1].top - playerCards[0].top, opponentCards[1].top - opponentCards[0].top, 0.001f)
        assertEquals(playerCards[1].bottom - playerCards[0].bottom, opponentCards[1].bottom - opponentCards[0].bottom, 0.001f)
    }
}
