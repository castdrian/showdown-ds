package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleCardContentTest {
    @Test
    fun compactAndSingleCardsUseTheSameReadablePokemonIdentity() {
        val single = BattleSession.PokemonDetails(
            name = "Alcremie-Caramel-Swirl",
            species = "Alcremie",
            types = listOf("FAIRY"),
            level = "50",
            gender = "♀",
            hp = "100/100",
            condition = "100/100",
            ability = "Sweet Veil",
            item = "Leftovers",
            moves = emptyList(),
            stats = ""
        )
        val compact = BattleSession.ActiveCombatant(
            slot = "p1a",
            name = single.name,
            species = single.species,
            types = single.types,
            level = single.level,
            gender = single.gender,
            hp = single.hp,
            condition = single.condition,
            entryAtNanos = 0L,
            dynamaxed = true
        )

        val singleContent = BattleCardContent.from(single)
        val compactContent = BattleCardContent.from(compact)
        val liveSingleContent = BattleCardContent.from(single, "87/100")

        assertEquals(singleContent, compactContent)
        assertEquals(singleContent.title, liveSingleContent.title)
        assertEquals(singleContent.levelLabel, liveSingleContent.levelLabel)
        assertEquals("87/100", liveSingleContent.hpLabel)
    }

    @Test
    fun faintedCardsHaveAnEmptyHealthBar() {
        val content = BattleCardContent.from("Iron Valiant", "Iron Valiant", "50", "", "0 fnt")

        assertEquals(0f, content.fraction, 0.001f)
    }
}
