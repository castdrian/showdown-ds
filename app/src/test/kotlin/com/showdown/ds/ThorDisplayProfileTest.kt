package com.showdown.ds

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
}
