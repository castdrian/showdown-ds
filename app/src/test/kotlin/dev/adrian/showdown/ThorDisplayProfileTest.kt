package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

    @Test
    fun keepsTheSecondaryPresentationInteractive() {
        val source = File("src/main/kotlin/dev/adrian/showdown/MainActivity.kt").readText()

        assertTrue(source.contains("clearFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)"))
        assertTrue(source.contains("context.createDisplayContext(display)"))
        assertTrue(source.contains("FrameLayout(presentationContext)"))
        assertTrue(source.contains("CommandDeckView(presentationContext"))
        assertTrue(source.contains("frame.isFocusableInTouchMode = true"))
        assertTrue(source.contains("frame.requestFocus()"))
        assertTrue(source.contains("configurePresentationWindow(presentation.window)"))
    }

    @Test
    fun keepsThorAvdMetadataAlignedWithTheCurrentRenderer() {
        val baseConfig = File("../config/avd/ayn-thor-base.ini").readText()
        val displayProfile = File("../config/avd/ayn-thor.ini").readText()
        val createScript = File("../scripts/create-ayn-thor-avd.sh").readText()

        assertTrue(baseConfig.contains("avd.ini.displayname = AYN Thor API 34"))
        assertTrue(baseConfig.contains("hw.gpu.mode = auto"))
        assertTrue(displayProfile.contains("hw.gpu.mode=auto"))
        assertTrue(createScript.contains("avd_name=\"AYN_Thor_API_34\""))
        assertFalse(baseConfig.contains("Vulkan", true))
        assertFalse(baseConfig.contains("lavapipe", true))
        assertFalse(createScript.contains("Vulkan", true))
        assertFalse(createScript.contains("lavapipe", true))
    }
}
