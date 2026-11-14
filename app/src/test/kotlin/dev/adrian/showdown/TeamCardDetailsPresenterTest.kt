package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class TeamCardDetailsPresenterTest {
    private fun details(
        level: String = "79",
        gender: String = "♂",
        item: String = "Leftovers",
        condition: String = "254/254"
    ) = BattleSession.PokemonDetails(
        name = "Tornadus",
        types = listOf("FLYING"),
        level = level,
        gender = gender,
        hp = "254/254",
        condition = condition,
        ability = "Prankster",
        item = item,
        moves = emptyList(),
        stats = ""
    )

    @Test
    fun TeamCardDetailsPresenter_SubtitleWithKnownItem_ShowsLevelGenderAndItem() {
        val subtitle = TeamCardDetailsPresenter.subtitle(details(), boosts = emptyMap())

        assertEquals("Lv. 79♂ · Leftovers", subtitle)
    }

    @Test
    fun TeamCardDetailsPresenter_SubtitleWithUnknownItem_OmitsTheItem() {
        val subtitle = TeamCardDetailsPresenter.subtitle(details(item = "Unknown item"), boosts = emptyMap())

        assertEquals("Lv. 79♂", subtitle)
    }

    @Test
    fun TeamCardDetailsPresenter_SubtitleWithBlankItem_OmitsTheItem() {
        val subtitle = TeamCardDetailsPresenter.subtitle(details(item = " "), boosts = emptyMap())

        assertEquals("Lv. 79♂", subtitle)
    }

    @Test
    fun TeamCardDetailsPresenter_SubtitleWithBoosts_ListsThemInStatOrderWithoutZeros() {
        val boosts = mapOf("spe" to -1, "atk" to 2, "def" to 0)

        val subtitle = TeamCardDetailsPresenter.subtitle(details(item = "Unknown item"), boosts)

        assertEquals("Lv. 79♂ · Atk +2 · Spe -1", subtitle)
    }

    @Test
    fun TeamCardDetailsPresenter_SubtitleWithItemAndBoosts_PutsTheItemBeforeTheBoosts() {
        val subtitle = TeamCardDetailsPresenter.subtitle(details(), mapOf("spa" to 1, "evasion" to 3))

        assertEquals("Lv. 79♂ · Leftovers · SpA +1 · Eva +3", subtitle)
    }

    @Test
    fun TeamCardDetailsPresenter_SubtitleWithoutGender_ShowsOnlyTheLevel() {
        val subtitle = TeamCardDetailsPresenter.subtitle(details(gender = "", item = ""), boosts = emptyMap())

        assertEquals("Lv. 79", subtitle)
    }

    @Test
    fun TeamCardDetailsPresenter_StateLabelWithMajorStatus_AppendsTheStatusToTheCardStatus() {
        val label = TeamCardDetailsPresenter.stateLabel("In battle", details(condition = "120/254 par"))

        assertEquals("In battle · PAR", label)
    }

    @Test
    fun TeamCardDetailsPresenter_StateLabelWithoutStatus_KeepsTheCardStatus() {
        val label = TeamCardDetailsPresenter.stateLabel("Available", details(condition = "254/254"))

        assertEquals("Available", label)
    }

    @Test
    fun TeamCardDetailsPresenter_StateLabelForAFaintedPokemon_KeepsTheCardStatus() {
        val label = TeamCardDetailsPresenter.stateLabel("Fainted", details(condition = "0 fnt"))

        assertEquals("Fainted", label)
    }

    @Test
    fun TeamCardDetailsPresenter_StateLabelIsCaseInsensitiveAboutTheStatus() {
        val label = TeamCardDetailsPresenter.stateLabel("Available", details(condition = "88/100 TOX"))

        assertEquals("Available · TOX", label)
    }
}
