package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSceneEffectsContractTest {
    @Test
    fun nativeBattleSceneDrawsFieldEffectsBetweenBackdropAndCombatants() {
        val source = File("src/main/kotlin/dev/adrian/showdown/BattleSceneView.kt").readText()
        val onDraw = source.substringAfter("override fun onDraw(canvas: Canvas)").substringBefore("override fun onTouchEvent")
        val backdropIndex = onDraw.indexOf("drawBackdrop(canvas, width, height)")
        val fieldIndex = onDraw.indexOf("drawFieldVisuals(canvas, width, height, scale, nowNanos, fieldVisuals)")
        val combatantIndex = onDraw.indexOf("drawCombatant(")

        assertTrue(source.contains("BattleFieldVisualComposer.compose(battleInfo)"))
        assertTrue(backdropIndex >= 0)
        assertTrue(fieldIndex > backdropIndex)
        assertTrue(combatantIndex > fieldIndex)
    }

    @Test
    fun lightweightMoveEffectsDispatchIntoMoveSpecificAnimations() {
        val source = File("src/main/kotlin/dev/adrian/showdown/BattleSceneView.kt").readText()
        val drawEffect = source.substringAfter("private fun drawLightweightMoveEffect(").substringBefore("private fun drawAttackMoveEffect(")

        assertTrue(drawEffect.contains("lightweightMoveActorSlot?.let"))
        assertTrue(drawEffect.contains("lightweightMoveTargetSlot?.let"))
        assertTrue(drawEffect.contains("when (lightweightMoveStyle)"))
        assertTrue(source.contains("private fun drawTypedMoveEffect("))
        assertTrue(source.contains("BattleMoveVisualStyleResolver.resolve("))
        assertTrue(source.contains("private fun drawContactStrikeEffect("))
        assertTrue(source.contains("private fun drawGroundRippleEffect("))
    }
}
