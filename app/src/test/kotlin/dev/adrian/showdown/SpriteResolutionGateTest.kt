package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpriteResolutionGateTest {
    @Test
    fun delayedAnimatedFallbackCanBeReplacedByPrimaryArtwork() {
        val received = mutableListOf<String?>()
        val gate = SpriteResolutionGate<String>(received::add)

        assertTrue(gate.beginFallback())
        gate.fallback("animated fallback")
        gate.primary("hd artwork")

        assertEquals(listOf("animated fallback", "hd artwork"), received)
    }

    @Test
    fun primaryArtworkPreventsDelayedFallbackFromDowngradingIt() {
        val received = mutableListOf<String?>()
        val gate = SpriteResolutionGate<String>(received::add)

        gate.primary("hd artwork")
        assertFalse(gate.beginFallback())

        assertEquals(listOf("hd artwork"), received)
    }

    @Test
    fun missingPrimaryAndFallbackArtworkReportsOneEmptyResult() {
        val received = mutableListOf<String?>()
        val gate = SpriteResolutionGate<String>(received::add)

        assertTrue(gate.beginFallback())
        gate.primary(null)
        gate.fallback(null)

        assertEquals(listOf<String?>(null), received)
    }
}
