package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownBattleMovePresentationTest {
    @Test
    fun usesAnInlineAnimationOverrideWithoutChangingTheDisplayedMove() {
        assertEquals(
            "Shadow Ball",
            ShowdownBattleMovePresentation.animationName(
                listOf("[anim] Shadow Ball"),
                "Psystrike"
            )
        )
    }

    @Test
    fun keepsTheOriginalMoveWhenNoAnimationOverrideIsPresent() {
        assertEquals(
            "Psystrike",
            ShowdownBattleMovePresentation.animationName(
                listOf("[miss]"),
                "Psystrike"
            )
        )
    }

    @Test
    fun ignoresAnEmptyAnimationOverride() {
        assertEquals(
            "Psystrike",
            ShowdownBattleMovePresentation.animationName(
                listOf("[anim]"),
                "Psystrike"
            )
        )
    }
}
