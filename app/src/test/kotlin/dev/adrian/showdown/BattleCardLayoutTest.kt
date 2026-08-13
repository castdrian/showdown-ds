package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleCardLayoutTest {
    @Test
    fun multiActiveCardsKeepReadableContentInsideTheirBounds() {
        val layout = BattleCardLayout.compactFor(3)

        assertTrue(layout.heightFraction > 0.06f)
        assertTrue(layout.titleBaselineFraction > 0.27f)
        assertTrue(layout.hpBaselineFraction < layout.barTopFraction)
        assertTrue(layout.barBottomFraction < 1f)
    }

    @Test
    fun singleAndDoubleCardsKeepTheirEstablishedGeometry() {
        val layout = BattleCardLayout.compactFor(2)

        assertEquals(0.085f, layout.heightFraction, 0.001f)
        assertEquals(0.012f, layout.gapFraction, 0.001f)
        assertEquals(0.29f, layout.titleBaselineFraction, 0.001f)
        assertEquals(0.51f, layout.hpBaselineFraction, 0.001f)
        assertEquals(0.55f, layout.barTopFraction, 0.001f)
        assertEquals(0.70f, layout.barBottomFraction, 0.001f)
    }

    @Test
    fun singlesAndDoublesUseTheSameCardTreatment() {
        assertEquals(BattleCardLayout.compactFor(1), BattleCardLayout.compactFor(2))
    }
}
