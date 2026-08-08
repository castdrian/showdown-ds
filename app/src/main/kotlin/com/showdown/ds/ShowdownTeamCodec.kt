package com.showdown.ds

data class ShowdownTeamSet(
    val nickname: String = "",
    val species: String = "",
    val item: String = "",
    val ability: String = "",
    val moves: List<String> = emptyList(),
    val nature: String = "",
    val evs: List<Int> = List(6) { 0 },
    val gender: String = "",
    val ivs: List<Int> = List(6) { 31 },
    val shiny: Boolean = false,
    val level: Int = 100,
    val happiness: Int = 255,
    val pokeBall: String = "",
    val hiddenPowerType: String = "",
    val gigantamax: Boolean = false,
    val dynamaxLevel: Int = 10,
    val teraType: String = ""
)

object ShowdownTeamCodec {
    fun validate(sets: List<ShowdownTeamSet>): List<String> {
        val errors = mutableListOf<String>()
        val populated = sets.filter { it.hasContent() }
        if (populated.isEmpty()) return listOf("Add at least one Pokémon to the team.")
        if (populated.size > 6) errors += "A team can contain at most six Pokémon."
        populated.forEachIndexed { index, set ->
            val label = "Pokémon ${index + 1}"
            if (set.species.isBlank() && set.nickname.isBlank()) errors += "$label needs a species."
            if (set.moves.size > 4) errors += "$label can have at most four moves."
            if (set.evs.size != 6 || set.evs.any { it !in 0..252 }) errors += "$label has invalid EVs."
            if (set.evs.sum() > 510) errors += "$label has more than 510 total EVs."
            if (set.ivs.size != 6 || set.ivs.any { it !in 0..31 }) errors += "$label has invalid IVs."
            if (set.level !in 1..100) errors += "$label has an invalid level."
            if (set.happiness !in 0..255) errors += "$label has invalid happiness."
            if (set.dynamaxLevel !in 0..10) errors += "$label has an invalid Dynamax level."
        }
        return errors
    }

    fun unpack(packed: String): List<ShowdownTeamSet> = packed
        .split(']')
        .mapNotNull { it.takeIf(String::isNotBlank)?.let(::unpackSet) }

    fun pack(sets: List<ShowdownTeamSet>): String = sets
        .filter { it.hasContent() }
        .joinToString("]", transform = ::packSet)

    private fun unpackSet(packed: String): ShowdownTeamSet {
        val fields = packed.split('|', limit = 12)
        val advanced = fields.getOrNull(11).orEmpty().split(',', limit = 6)
        return ShowdownTeamSet(
            nickname = fields.value(0),
            species = fields.value(1).ifBlank { fields.value(0) },
            item = fields.value(2),
            ability = fields.value(3),
            moves = fields.value(4).split(',').filter(String::isNotBlank),
            nature = fields.value(5),
            evs = parseValues(fields.value(6), 0, 0, 252),
            gender = fields.value(7),
            ivs = parseValues(fields.value(8), 31, 0, 31),
            shiny = fields.value(9) == "S",
            level = fields.value(10).toIntOrNull()?.coerceIn(1, 100) ?: 100,
            happiness = advanced.value(0).toIntOrNull()?.coerceIn(0, 255) ?: 255,
            pokeBall = advanced.value(1),
            hiddenPowerType = advanced.value(2),
            gigantamax = advanced.value(3) == "G",
            dynamaxLevel = advanced.value(4).toIntOrNull()?.coerceIn(0, 10) ?: 10,
            teraType = advanced.value(5)
        )
    }

    private fun packSet(set: ShowdownTeamSet): String {
        val nickname = set.nickname.trim()
        val species = set.species.trim().takeUnless { it.isBlank() || it.equals(nickname, true) }.orEmpty()
        val fields = listOf(
            nickname,
            species,
            packedId(set.item),
            packedId(set.ability),
            set.moves.map(::packedId).filter(String::isNotBlank).take(4).joinToString(","),
            set.nature.trim(),
            packValues(set.evs, 0, 252),
            set.gender.trim().uppercase().takeIf { it == "M" || it == "F" }.orEmpty(),
            packValues(set.ivs, 31, 31),
            if (set.shiny) "S" else "",
            set.level.coerceIn(1, 100).takeUnless { it == 100 }?.toString().orEmpty(),
            packAdvanced(set)
        )
        return fields.joinToString("|")
    }

    private fun packAdvanced(set: ShowdownTeamSet): String {
        val values = listOf(
            set.happiness.coerceIn(0, 255).takeUnless { it == 255 }?.toString().orEmpty(),
            packedId(set.pokeBall),
            set.hiddenPowerType.trim(),
            if (set.gigantamax) "G" else "",
            set.dynamaxLevel.coerceIn(0, 10).takeUnless { it == 10 }?.toString().orEmpty(),
            set.teraType.trim()
        )
        return values.joinToString(",").trimEnd(',')
    }

    private fun packValues(values: List<Int>, default: Int, maximum: Int): String {
        val normalized = (0 until 6).map { values.getOrNull(it)?.coerceIn(0, maximum) ?: default }
        if (normalized.all { it == default }) return ""
        return normalized.joinToString(",") { value -> value.takeUnless { it == default }?.toString().orEmpty() }
    }

    private fun parseValues(value: String, default: Int, minimum: Int, maximum: Int): List<Int> {
        if (value.isBlank()) return List(6) { default }
        return value.split(',', limit = 6).let { values ->
            (0 until 6).map { index -> values.getOrNull(index)?.toIntOrNull()?.coerceIn(minimum, maximum) ?: default }
        }
    }

    private fun ShowdownTeamSet.hasContent() = listOf(nickname, species, item, ability, nature, gender, pokeBall, hiddenPowerType, teraType).any(String::isNotBlank) || moves.isNotEmpty()

    private fun packedId(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun List<String>.value(index: Int) = getOrNull(index).orEmpty()
}
