package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFeedTextTest {
    @Test
    fun keepsAReadableBattleEventAcrossTwoLines() {
        val event = "The opposing Pokémon fainted"
        val measure: (String) -> Float = { it.length * 10f }
        val lines = BattleFeedText.wrap(event, 160f, 2, measure)

        assertEquals(event, lines.joinToString(" "))
        assertTrue(lines.size <= 2)
        assertTrue(lines.all { measure(it) <= 160f })
    }

    @Test
    fun newestWindowKeepsCompleteEventsTogether() {
        val entries = listOf(
            listOf("Battle timer is ON", "inactive players will lose"),
            listOf("A move was used."),
            listOf("The target lost health."),
            listOf("The battle is waiting.")
        )

        assertEquals(
            listOf("A move was used.", "The target lost health.", "The battle is waiting."),
            BattleFeedText.window(entries, 3, 0)
        )
    }

    @Test
    fun scrolledWindowAlsoEndsOnCompleteEvents() {
        val entries = listOf(
            listOf("Battle timer is ON", "inactive players will lose"),
            listOf("A move was used."),
            listOf("The target lost health."),
            listOf("The battle is waiting.")
        )

        assertEquals(
            listOf("Battle timer is ON", "inactive players will lose", "A move was used."),
            BattleFeedText.window(entries, 3, 3)
        )
    }
}
