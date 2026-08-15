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
    fun onlyAcceptsTheThorLowerDisplayGeometry() {
        assertTrue(ThorDisplayProfile.isThorLowerDisplay(1240, 1080))
        assertTrue(ThorDisplayProfile.isThorLowerDisplay(1080, 1240))
        assertFalse(ThorDisplayProfile.isThorLowerDisplay(590, 1280))
        assertFalse(ThorDisplayProfile.isThorLowerDisplay(1920, 1080))
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

        assertTrue(source.contains("clearFlags("))
        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or"))
        assertTrue(source.contains("WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE"))
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
        val runScript = File("../scripts/run-ayn-thor-avd.sh").readText()
        val overlayPatch = File("../tools/android-emulator/ayn-thor-single-window.patch").readText()

        assertTrue(baseConfig.contains("avd.ini.displayname = AYN Thor API 34"))
        assertTrue(baseConfig.contains("hw.gpu.mode = auto"))
        assertTrue(displayProfile.contains("hw.gpu.mode=auto"))
        assertTrue(createScript.contains("avd_name=\"AYN_Thor_API_34\""))
        assertTrue(runScript.contains("-feature MultiDisplay"))
        assertTrue(runScript.contains("-multidisplay \"1,1240,1080,420,1347\""))
        assertTrue(runScript.contains("com.android.emulator.multidisplay.START"))
        assertTrue(runScript.contains("wait_for_android_boot()"))
        assertTrue(runScript.contains("activate_secondary_display()"))
        val assignmentPattern = Regex("""^\+\s*(primary|thorDisplay)->second\.(width|height|pos_x|pos_y) = ([^;]+);$""")
        val layoutAssignments = overlayPatch.lineSequence()
            .mapNotNull { line ->
                assignmentPattern.matchEntire(line)?.let { match ->
                    "${match.groupValues[1]}->second.${match.groupValues[2]}" to match.groupValues[3].trim()
                }
            }
            .toMap()
        assertEquals(
            mapOf(
                "primary->second.width" to "primary->second.originalWidth",
                "primary->second.height" to "primary->second.originalHeight",
                "primary->second.pos_x" to "0",
                "primary->second.pos_y" to "0",
                "thorDisplay->second.width" to "thorDisplay->second.originalWidth",
                "thorDisplay->second.height" to "thorDisplay->second.originalHeight",
                "thorDisplay->second.pos_x" to "340",
                "thorDisplay->second.pos_y" to "1080"
            ),
            layoutAssignments
        )
        assertTrue(
            layoutAssignments.getValue("primary->second.pos_y").toInt() <
                layoutAssignments.getValue("thorDisplay->second.pos_y").toInt()
        )
        assertFalse(baseConfig.contains("Vulkan", true))
        assertFalse(baseConfig.contains("lavapipe", true))
        assertFalse(createScript.contains("Vulkan", true))
        assertFalse(createScript.contains("lavapipe", true))
    }
}
