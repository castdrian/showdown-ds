package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleTeamPreviewLayoutTest {
    @Test
    fun fullTeamUsesTwoReadableRowsWithoutOverlap() {
        val slots = BattleTeamPreviewLayout.slots(1920f, 1080f, 6)

        assertEquals(6, slots.size)
        assertTrue(slots.all { it.width > 400f })
        assertTrue(slots.all { it.height > 250f })
        assertTrue(slots.zipWithNext().all { (first, second) -> first.right <= second.left || first.bottom <= second.top })
        assertTrue(slots.last().bottom <= 1080f)
    }

    @Test
    fun partialLastRowStaysCentered() {
        val slots = BattleTeamPreviewLayout.slots(1920f, 1080f, 5)

        assertEquals(5, slots.size)
        assertEquals(slots[0].left, slots[3].left - (slots[1].left - slots[0].left) / 2f, 0.01f)
        assertTrue(slots[3].left > slots[0].left)
        assertTrue(slots[4].right < slots[2].right)
    }

    @Test
    fun pageNavigationFitsAboveThePokemonCards() {
        val controls = BattleTeamPreviewLayout.navigationSlots(1920f, 1080f)
        val cards = BattleTeamPreviewLayout.slots(1920f, 1080f, 6)

        assertEquals(2, controls.size)
        assertTrue(controls.all { it.left >= 0f && it.right <= 1920f })
        assertTrue(controls.all { it.bottom < cards.first().top })
        assertTrue(controls.first().right < 500f)
        assertTrue(controls.last().left > 1400f)
    }

    @Test
    fun upperTeamPreviewUsesPagedAbsoluteIndexesInsteadOfTruncatingTheRoster() {
        val source = File("src/main/kotlin/dev/adrian/showdown/BattleSceneView.kt").readText()

        assertTrue(source.contains("val visibleIndices = opponentPreviewIndices(party)"))
        assertTrue(source.contains("ACCESSIBLE_TEAM_PREVIEW_BASE + teamIndex"))
        assertTrue(source.contains("previewSprites[teamIndex]?.draw("))
        assertTrue(source.contains("changeOpponentPreviewPage(if (previous) -1 else 1)"))
        assertTrue(source.contains("abs(horizontalDelta) > width * 0.08f"))
        assertFalse(source.contains("session.opponentPartyDetails().take(6)"))
    }
}
