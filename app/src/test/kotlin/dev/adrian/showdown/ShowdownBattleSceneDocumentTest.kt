package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownBattleSceneDocumentTest {
    private val template = File("src/main/assets/showdown-battle-scene.html").readText()

    @Test
    fun ShowdownBattleSceneDocument_RenderRealTemplate_LeavesNoPlaceholders() {
        val rendered = ShowdownBattleSceneDocument.render(template, debug = false)

        assertEquals(emptyList<String>(), Regex("__[A-Z_]+__").findAll(rendered).map { it.value }.toList())
    }

    @Test
    fun ShowdownBattleSceneDocument_Render_ClampsAnimationSpeedToThePlaybackRange() {
        val rendered = ShowdownBattleSceneDocument.render(template, debug = false)

        val expected = "Math.max(${BattlePlaybackSpeed.MINIMUM}, Math.min(${BattlePlaybackSpeed.MAXIMUM}, Number(speed) || 1))"
        assertTrue(rendered.contains(expected))
    }

    @Test
    fun ShowdownBattleSceneDocument_Render_GuardsEverySupportedPerspective() {
        val rendered = ShowdownBattleSceneDocument.render(template, debug = false)

        assertTrue(rendered.contains("if (${ShowdownBattlePerspective.javascriptGuard}) return;"))
    }

    @Test
    fun ShowdownBattleSceneDocument_RenderDebug_EnablesDiagnosticsOnlyWhenRequested() {
        val debugRendered = ShowdownBattleSceneDocument.render(template, debug = true)
        val releaseRendered = ShowdownBattleSceneDocument.render(template, debug = false)

        assertTrue(debugRendered.contains("if (true) {"))
        assertFalse(releaseRendered.contains("if (true) {"))
        assertTrue(releaseRendered.contains("if (false) {"))
    }
}
