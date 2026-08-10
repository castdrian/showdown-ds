package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThorDisplayProfileTest {
    @Test
    fun classifiesThorDisplaysByTheirPhysicalPanelGeometry() {
        assertEquals(
            ThorDisplayKind.UPPER,
            ThorDisplayProfile.kindFor(ThorDisplayProfile.UPPER_WIDTH_PIXELS, ThorDisplayProfile.UPPER_HEIGHT_PIXELS)
        )
        assertEquals(
            ThorDisplayKind.LOWER,
            ThorDisplayProfile.kindFor(ThorDisplayProfile.LOWER_WIDTH_PIXELS, ThorDisplayProfile.LOWER_HEIGHT_PIXELS)
        )
    }

    @Test
    fun givesTheSmallerPanelAReadablePixelFloor() {
        assertTrue(
            ThorDisplayProfile.minimumReadablePixels(
                ThorDisplayProfile.LOWER_WIDTH_PIXELS,
                ThorDisplayProfile.LOWER_HEIGHT_PIXELS
            ) > ThorDisplayProfile.minimumReadablePixels(
                ThorDisplayProfile.UPPER_WIDTH_PIXELS,
                ThorDisplayProfile.UPPER_HEIGHT_PIXELS
            )
        )
    }

    @Test
    fun scalesTheLowerPanelFloorToEighteenSpAtThorDensity() {
        assertEquals(
            ThorDisplayProfile.LOWER_MINIMUM_TEXT_SP * 2.625f,
            ThorDisplayProfile.minimumReadablePixels(
                ThorDisplayProfile.LOWER_WIDTH_PIXELS,
                ThorDisplayProfile.LOWER_HEIGHT_PIXELS,
                2.625f
            ),
            0.001f
        )
    }

    @Test
    fun supportsCompactSupportingTextWithoutLoweringPrimaryTextFloor() {
        assertEquals(
            14f * 2.625f,
            ThorDisplayProfile.minimumReadablePixels(
                ThorDisplayProfile.LOWER_WIDTH_PIXELS,
                ThorDisplayProfile.LOWER_HEIGHT_PIXELS,
                2.625f,
                14f
            ),
            0.001f
        )
        assertEquals(
            ThorDisplayProfile.LOWER_MINIMUM_TEXT_SP * 2.625f,
            ThorDisplayProfile.minimumReadablePixels(
                ThorDisplayProfile.LOWER_WIDTH_PIXELS,
                ThorDisplayProfile.LOWER_HEIGHT_PIXELS,
                2.625f
            ),
            0.001f
        )
    }
}
