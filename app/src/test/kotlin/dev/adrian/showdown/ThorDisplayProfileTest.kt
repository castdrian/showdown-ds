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
    fun exposesPhysicalPanelBoundsForSizingChecks() {
        assertEquals(132.83f, ThorDisplayProfile.physicalWidthMillimetres(ThorDisplayKind.UPPER), 0.01f)
        assertEquals(74.72f, ThorDisplayProfile.physicalHeightMillimetres(ThorDisplayKind.UPPER), 0.01f)
        assertEquals(75.11f, ThorDisplayProfile.physicalWidthMillimetres(ThorDisplayKind.LOWER), 0.01f)
        assertEquals(65.42f, ThorDisplayProfile.physicalHeightMillimetres(ThorDisplayKind.LOWER), 0.01f)
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
        assertTrue(source.contains("val presentationContext = getContext()"))
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
        val buildScript = File("../scripts/build-ayn-thor-emulator-overlay.sh").readText()
        val installScript = File("../scripts/install-ayn-thor-emulator-overlay.sh").readText()
        val overlayPatch = File("../tools/android-emulator/ayn-thor-single-window.patch").readText()

        assertTrue(baseConfig.contains("avd.ini.displayname = AYN Thor API 34"))
        assertTrue(baseConfig.contains("hw.gpu.mode = auto"))
        assertTrue(baseConfig.contains("hw.display1.yOffset = 0"))
        assertTrue(displayProfile.contains("hw.gpu.mode=auto"))
        assertTrue(displayProfile.contains("hw.display1.yOffset=0"))
        assertTrue(createScript.contains("avd_name=\"AYN_Thor_API_34\""))
        assertTrue(runScript.contains("-feature MultiDisplay"))
        assertTrue(runScript.contains("-multidisplay \"1,1240,1080,420,1347\""))
        assertTrue(runScript.contains("window_scale=\"\${AYN_THOR_WINDOW_SCALE:-auto}\""))
        assertTrue(runScript.contains("thor_preview_width_millimetres=\"132.83\""))
        assertTrue(runScript.contains("scale_macos_preview()"))
        assertTrue(runScript.contains("CGDisplayScreenSize"))
        assertTrue(runScript.contains("targetWidthMillimetres"))
        assertTrue(runScript.contains("tell process \"qemu-system-aarch64\""))
        assertTrue(runScript.contains("previewScale"))
        assertTrue(runScript.contains("Unable to scale the macOS Thor preview window"))
        assertTrue(runScript.contains("com.android.emulator.multidisplay.START"))
        assertTrue(runScript.contains("wait_for_android_boot()"))
        assertTrue(runScript.contains("activate_secondary_display()"))
        assertTrue(runScript.contains("verify_thor_displays()"))
        assertTrue(runScript.contains("thorPreviewWidth"))
        assertTrue(runScript.contains("thorPreviewHeight"))
        assertTrue(runScript.contains("lower_input_scale_count"))
        assertTrue(runScript.contains("set_avd_config \"hw.display1.yOffset\" \"0\""))
        assertTrue(runScript.contains("logicalFrame=Rect\\(0, 0 - 1920, 1080\\)"))
        assertTrue(runScript.contains("logicalFrame=Rect\\(0, 0 - 1240, 1080\\)"))
        assertTrue(runScript.contains("AYN_THOR_ALLOW_STOCK_EMULATOR"))
        assertTrue(runScript.contains("The patched AYN Thor emulator overlay is missing"))
        assertTrue(runScript.contains("overlay_patch_digest_file"))
        assertTrue(runScript.contains("The AYN Thor emulator overlay is stale"))
        assertTrue(runScript.contains("snapshot_args=(-no-snapshot)"))
        assertTrue(runScript.indexOf("\"\${snapshot_args[@]}\"") > runScript.indexOf("\"\$@\""))
        assertTrue(runScript.indexOf("if ! verify_thor_displays; then") > runScript.indexOf("if ! activate_secondary_display; then"))
        assertTrue(runScript.contains("while (( attempt < 20 )); do"))
        assertTrue(buildScript.contains("apply --reverse --check"))
        assertTrue(buildScript.contains("ccache_mode"))
        assertTrue(buildScript.contains("rebuild.sh\" --ccache \"\$ccache_mode\""))
        assertTrue(installScript.contains("The overlay path is not a directory"))
        assertTrue(installScript.contains("ayn-thor-single-window.patch.sha256"))
        assertTrue(installScript.contains("qemu_headless_name"))
        assertTrue(installScript.contains("qemu_headless_binary"))
        assertTrue(buildScript.contains("install-ayn-thor-emulator-overlay.sh\" \"\$qemu_binary\" \"\$qemu_headless_binary\" \"\$build_root\""))
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
                "primary->second.pos_y" to "thorPreviewHeight",
                "thorDisplay->second.width" to "thorPreviewWidth",
                "thorDisplay->second.height" to "thorPreviewHeight",
                "thorDisplay->second.pos_x" to "(primary->second.originalWidth - thorPreviewWidth) / 2",
                "thorDisplay->second.pos_y" to "0"
            ),
            layoutAssignments
        )
        assertEquals("thorPreviewHeight", layoutAssignments.getValue("primary->second.pos_y"))
        assertEquals("0", layoutAssignments.getValue("thorDisplay->second.pos_y"))
        assertTrue(overlayPatch.contains("constexpr uint32_t thorPreviewWidth = 1086;"))
        assertTrue(overlayPatch.contains("constexpr uint32_t thorPreviewHeight = 946;"))
        assertTrue(overlayPatch.contains("std::min(*x * iter.second.originalWidth / iter.second.width"))
        assertTrue(overlayPatch.contains("std::min(*y * iter.second.originalHeight / iter.second.height"))
        assertTrue(overlayPatch.contains("getNumberActiveMultiDisplaysLocked() == 1"))
        assertTrue(overlayPatch.contains("thorDisplay->second.cb == 0"))
        assertTrue(overlayPatch.contains("void MultiDisplay::performRotationLocked(int mOrientation) {"))
        assertTrue(overlayPatch.contains("if (mOrientation == SKIN_ROTATION_0) {"))
        assertFalse(baseConfig.contains("Vulkan", true))
        assertFalse(baseConfig.contains("lavapipe", true))
        assertFalse(createScript.contains("Vulkan", true))
        assertFalse(createScript.contains("lavapipe", true))
    }
}
