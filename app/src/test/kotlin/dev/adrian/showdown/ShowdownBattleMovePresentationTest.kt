package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownBattleMovePresentationTest {
    @Test
    fun suppressesAnimationWhenTheProtocolMarksTheMoveStill() {
        assertFalse(ShowdownBattleMovePresentation.shouldAnimate(listOf("[still]")))
    }

    @Test
    fun animatesMovesWithoutTheStillMarker() {
        assertTrue(ShowdownBattleMovePresentation.shouldAnimate(listOf("[miss]", "[anim] Shadow Ball")))
    }

    @Test
    fun recognizesTheStillMarkerAmongOtherProtocolArguments() {
        assertFalse(ShowdownBattleMovePresentation.shouldAnimate(listOf("[from] confusion", "[still]", "[anim] Tackle")))
    }

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

    @Test
    fun parsesExplicitProtocolAnimationEvents() {
        assertEquals(
            ShowdownBattleMovePresentation.ProtocolAnimation(
                actor = "p1a: Pikachu",
                moveName = "Thunderbolt",
                target = "p2a: Gyarados",
                shouldAnimate = true
            ),
            ShowdownBattleMovePresentation.protocolAnimation(
                listOf("", "-anim", "p1a: Pikachu", "Thunderbolt", "p2a: Gyarados", "[anim] Thunderbolt")
            )
        )
    }

    @Test
    fun preservesStillMarkersOnExplicitProtocolAnimationEvents() {
        assertEquals(
            false,
            ShowdownBattleMovePresentation.protocolAnimation(
                listOf("", "-anim", "p1a: Pikachu", "Thunderbolt", "p2a: Gyarados", "[still]")
            )?.shouldAnimate
        )
    }

    @Test
    fun ignoresMalformedProtocolAnimationEvents() {
        assertEquals(
            null,
            ShowdownBattleMovePresentation.protocolAnimation(listOf("", "-anim", "p1a: Pikachu"))
        )
    }
}
