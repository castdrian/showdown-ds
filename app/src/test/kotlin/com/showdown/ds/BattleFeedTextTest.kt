package com.showdown.ds

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
}
