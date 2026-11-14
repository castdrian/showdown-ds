package dev.adrian.showdown

import java.util.Locale

/** Text for the Pokémon-tab team cards beyond name, HP and types. */
object TeamCardDetailsPresenter {
    private const val SEPARATOR = " · "
    private const val UNKNOWN_ITEM = "Unknown item"
    private val boostLabels = linkedMapOf(
        "atk" to "Atk",
        "def" to "Def",
        "spa" to "SpA",
        "spd" to "SpD",
        "spe" to "Spe",
        "accuracy" to "Acc",
        "evasion" to "Eva"
    )
    private val majorStatuses = setOf("par", "brn", "psn", "tox", "slp", "frz")

    /** "Lv. 79♂", then the held item if known, then any stat boosts (for the Pokémon on the field). */
    fun subtitle(details: BattleSession.PokemonDetails, boosts: Map<String, Int>): String =
        (listOf("Lv. ${details.level}${details.gender}") + knownItem(details) + boostLabels(boosts))
            .joinToString(SEPARATOR)

    /** The card's availability status, with the Pokémon's major status condition appended. */
    fun stateLabel(cardStatus: String, details: BattleSession.PokemonDetails): String {
        val status = majorStatus(details.condition) ?: return cardStatus
        return "$cardStatus$SEPARATOR$status"
    }

    private fun knownItem(details: BattleSession.PokemonDetails): List<String> =
        listOfNotNull(details.item.trim().takeUnless { it.isEmpty() || it == UNKNOWN_ITEM })

    private fun boostLabels(boosts: Map<String, Int>): List<String> = boostLabels.mapNotNull { (stat, label) ->
        val stage = boosts[stat] ?: return@mapNotNull null
        if (stage == 0) null else "$label ${if (stage > 0) "+" else ""}$stage"
    }

    private fun majorStatus(condition: String): String? =
        condition.trim().substringAfterLast(' ', "").lowercase(Locale.ROOT)
            .takeIf { it in majorStatuses }?.uppercase(Locale.ROOT)
}
