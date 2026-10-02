package dev.adrian.showdown

internal object BattleAccessibilityText {
    fun pokemon(name: String, level: String, gender: String, hp: String, condition: String): String {
        val genderLabel = when (gender.trim().lowercase()) {
            "♀", "f" -> "female"
            "♂", "m" -> "male"
            else -> gender.trim().takeIf(String::isNotBlank)
        }
        val conditionLabel = conditionLabel(condition)
        return buildList {
            add(name.ifBlank { "Unknown Pokémon" })
            add("level $level")
            genderLabel?.let(::add)
            if (hp.isNotBlank()) add("HP $hp")
            conditionLabel?.let(::add)
        }.joinToString(", ")
    }

    fun inspectDetails(
        details: BattleSession.PokemonDetails,
        visibleName: String,
        effects: List<String>
    ): String {
        val summary = buildList {
            add(pokemon(visibleName, details.level, details.gender, details.hp, details.condition))
            if (details.types.isNotEmpty()) add("types ${details.types.joinToString()}")
            if (details.possibleAbilities.isNotEmpty()) {
                add("possible abilities ${details.possibleAbilities.joinToString()}")
            } else {
                add("ability ${details.ability}")
            }
            add("item ${BattleItemPresentation.visibleName(details.item) ?: "Unknown item"}")
            if (details.stats.isNotBlank()) add("stats ${details.stats.replace("\n", ", ")}")
            if (details.moves.isNotEmpty()) add("moves ${details.moves.joinToString()}")
            if (effects.isNotEmpty()) add("active effects ${effects.joinToString()}")
        }.joinToString(". ")
        return "Pokémon details for $visibleName. $summary. Activate to close details."
    }

    fun move(
        name: String,
        type: String,
        pp: Int,
        maxPp: Int,
        category: String,
        power: String,
        accuracy: String,
        disabled: Boolean
    ): String {
        val disabledLabel = if (disabled) ", disabled" else ""
        val powerLabel = power.takeUnless(::isUnavailable)?.let { "power $it" } ?: "power unavailable"
        val accuracyLabel = accuracy.takeUnless(::isUnavailable)?.let { value ->
            val numericValue = value.removeSuffix("%").trim()
            val spokenValue = numericValue.toDoubleOrNull()?.let { "$numericValue percent" } ?: value
            "accuracy $spokenValue"
        } ?: "accuracy unavailable"
        return "$name, ${title(type)} type, $powerLabel, $accuracyLabel, $pp of $maxPp PP, ${title(category)}$disabledLabel"
    }

    fun target(label: String) = "Target $label"

    fun gimmick(label: String, teraType: String? = null) =
        if (teraType.isNullOrBlank()) label else "$label, ${title(teraType)} type"

    private fun conditionLabel(condition: String): String? = when (condition.trim().uppercase()) {
        "", "READY", "OK" -> null
        "FNT", "FAINTED" -> "Fainted"
        "BRN", "BURN" -> "Burned"
        "FRZ", "FROZEN" -> "Frozen"
        "PAR", "PARALYSIS" -> "Paralyzed"
        "PSN", "POISON" -> "Poisoned"
        "TOX", "TOXIC" -> "Badly poisoned"
        "SLP", "SLEEP" -> "Asleep"
        else -> condition.trim().lowercase().replaceFirstChar(Char::uppercase)
    }

    private fun isUnavailable(value: String) = value.isBlank() || value == "—" || value == "-"

    private fun title(value: String): String = value
        .trim()
        .replace('_', ' ')
        .lowercase()
        .split(Regex("\\s+"))
        .filter(String::isNotBlank)
        .joinToString(" ") { word -> word.replaceFirstChar(Char::uppercase) }
}
