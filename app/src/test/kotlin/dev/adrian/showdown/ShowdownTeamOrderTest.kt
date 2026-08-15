package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownTeamOrderTest {
    private val sets = listOf(
        ShowdownTeamSet(species = "Pikachu"),
        ShowdownTeamSet(species = "Gholdengo"),
        ShowdownTeamSet(species = "Ogerpon")
    )

    @Test
    fun movesASetTowardTheFront() {
        assertEquals(
            listOf("Gholdengo", "Pikachu", "Ogerpon"),
            ShowdownTeamOrder.move(sets, 1, -1).map(ShowdownTeamSet::species)
        )
    }

    @Test
    fun movesASetTowardTheBack() {
        assertEquals(
            listOf("Pikachu", "Ogerpon", "Gholdengo"),
            ShowdownTeamOrder.move(sets, 1, 1).map(ShowdownTeamSet::species)
        )
    }

    @Test
    fun clampsMovesAtTheTeamEdges() {
        assertEquals(sets, ShowdownTeamOrder.move(sets, 0, -1))
        assertEquals(sets, ShowdownTeamOrder.move(sets, sets.lastIndex, 1))
        assertEquals(sets, ShowdownTeamOrder.move(sets, 99, -1))
    }
}
