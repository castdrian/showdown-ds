package dev.adrian.showdown

import org.json.JSONArray
import org.json.JSONObject

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

    fun parse(value: String): List<ShowdownTeamSet> {
        val input = value.trim()
        if (input.isBlank()) return emptyList()
        return when {
            input.startsWith("[") || input.startsWith("{") -> parseJson(input)
            '|' in input || (']' in input && !looksLikeBetaClientExport(input)) -> unpack(input)
            else -> parseText(input)
        }
    }

    fun pack(sets: List<ShowdownTeamSet>): String = sets
        .filter { it.hasContent() }
        .joinToString("]", transform = ::packSet)

    fun toText(sets: List<ShowdownTeamSet>): String = sets
        .filter { it.hasContent() }
        .joinToString("\n\n", transform = ::textSet)

    fun toJson(sets: List<ShowdownTeamSet>): String = JSONArray().apply {
        sets.filter { it.hasContent() }.forEach { put(jsonSet(it)) }
    }.toString()

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

    private fun parseText(input: String): List<ShowdownTeamSet> = input
        .split(Regex("\\r?\\n\\s*\\r?\\n"))
        .mapNotNull(::parseTextSet)

    private fun looksLikeBetaClientExport(input: String): Boolean = input.lineSequence()
        .map(String::trim)
        .any { Regex("^\\[[^\\]]+](?:\\s*@.*)?$").matches(it) }

    private fun parseJson(input: String): List<ShowdownTeamSet> = runCatching {
        val values = if (input.startsWith("[")) JSONArray(input) else JSONArray().put(JSONObject(input))
        buildList {
            for (index in 0 until values.length()) {
                values.optJSONObject(index)?.let(::parseJsonSet)?.let(::add)
            }
        }
    }.getOrDefault(emptyList())

    private fun parseJsonSet(value: JSONObject): ShowdownTeamSet {
        val nickname = value.optString("name")
        val species = value.optString("species").ifBlank { nickname }
        return ShowdownTeamSet(
            nickname = nickname,
            species = species,
            item = value.optString("item"),
            ability = value.optString("ability"),
            moves = value.optJSONArray("moves")?.let { moves ->
                buildList {
                    for (index in 0 until moves.length()) moves.optString(index).takeIf(String::isNotBlank)?.let(::add)
                }
            }.orEmpty(),
            nature = value.optString("nature"),
            evs = jsonStatValues(value.optJSONObject("evs"), 0),
            gender = value.optString("gender"),
            ivs = jsonStatValues(value.optJSONObject("ivs"), 31),
            shiny = value.optBoolean("shiny"),
            level = value.optInt("level", 100),
            happiness = value.optInt("happiness", 255),
            pokeBall = value.optString("pokeball", value.optString("pokeBall")),
            hiddenPowerType = value.optString(
                "hiddenpowertype",
                value.optString(
                    "hpType",
                    value.optString("hiddenpower", value.optString("hiddenPower"))
                )
            ),
            gigantamax = value.optBoolean("gigantamax"),
            dynamaxLevel = value.optInt("dynamaxlevel", value.optInt("dynamaxLevel", 10)),
            teraType = value.optString("teratype", value.optString("teraType"))
        )
    }

    private fun jsonStatValues(value: JSONObject?, default: Int): List<Int> {
        val names = listOf("hp", "atk", "def", "spa", "spd", "spe")
        return names.map { name -> value?.optInt(name, default) ?: default }
    }

    private fun jsonSet(set: ShowdownTeamSet) = JSONObject().apply {
        if (set.nickname.isNotBlank()) put("name", set.nickname.trim())
        if (set.species.isNotBlank()) put("species", set.species.trim())
        if (set.item.isNotBlank()) put("item", set.item.trim())
        if (set.ability.isNotBlank()) put("ability", set.ability.trim())
        if (set.moves.isNotEmpty()) put("moves", JSONArray(set.moves.take(4)))
        if (set.nature.isNotBlank()) put("nature", set.nature.trim())
        put("evs", jsonStats(set.evs, 0))
        if (set.gender.isNotBlank()) put("gender", set.gender.trim())
        put("ivs", jsonStats(set.ivs, 31))
        if (set.shiny) put("shiny", true)
        if (set.level != 100) put("level", set.level.coerceIn(1, 100))
        if (set.happiness != 255) put("happiness", set.happiness.coerceIn(0, 255))
        if (set.pokeBall.isNotBlank()) put("pokeball", set.pokeBall.trim())
        if (set.hiddenPowerType.isNotBlank()) put("hiddenpowertype", set.hiddenPowerType.trim())
        if (set.gigantamax) put("gigantamax", true)
        if (set.dynamaxLevel != 10) put("dynamaxlevel", set.dynamaxLevel.coerceIn(0, 10))
        if (set.teraType.isNotBlank()) put("teratype", set.teraType.trim())
    }

    private fun jsonStats(values: List<Int>, default: Int) = JSONObject().apply {
        listOf("hp", "atk", "def", "spa", "spd", "spe").forEachIndexed { index, name ->
            values.getOrNull(index)?.takeUnless { it == default }?.let { put(name, it) }
        }
    }

    private fun parseTextSet(block: String): ShowdownTeamSet? {
        val lines = block.lines().map(String::trim).filter(String::isNotBlank)
        val header = lines.firstOrNull() ?: return null
        var item = header.substringAfter(" @ ", "").trim()
        val subject = header.substringBefore(" @ ").trim()
        val gender = Regex("\\s\\(([MF])\\)$").find(subject)?.groupValues?.get(1).orEmpty()
        val withoutGender = subject.replace(Regex("\\s\\([MF]\\)$"), "").trim()
        val speciesMatch = Regex("^(.+) \\(([^()]*)\\)$").matchEntire(withoutGender)
        val nickname = speciesMatch?.groupValues?.get(1).orEmpty()
        val species = speciesMatch?.groupValues?.get(2).orEmpty().ifBlank { withoutGender }
        val moves = mutableListOf<String>()
        var ability = ""
        var nature = ""
        var level = 100
        var happiness = 255
        var shiny = false
        var pokeBall = ""
        var hiddenPowerType = ""
        var gigantamax = false
        var dynamaxLevel = 10
        var teraType = ""
        var evs = List(6) { 0 }
        var ivs = List(6) { 31 }
        lines.drop(1).forEach { line ->
            val betaAbility = Regex("^\\[([^\\]]+)](?:\\s*@\\s*(.*))?$").matchEntire(line)
            when {
                betaAbility != null -> {
                    ability = betaAbility.groupValues[1].trim()
                    item = betaAbility.groupValues.getOrNull(2).orEmpty().trim()
                }
                line.startsWith("Ability:", true) -> ability = line.substringAfter(':').trim()
                line.endsWith(" Nature", true) -> nature = line.removeSuffix(" Nature").trim()
                line.startsWith("Level:", true) -> level = line.substringAfter(':').trim().toIntOrNull() ?: 100
                line.startsWith("Happiness:", true) -> happiness = line.substringAfter(':').trim().toIntOrNull() ?: 255
                line.startsWith("Shiny:", true) -> shiny = line.substringAfter(':').trim().equals("yes", true)
                line.startsWith("Hidden Power:", true) -> hiddenPowerType = line.substringAfter(':').trim()
                line.startsWith("Gigantamax:", true) -> gigantamax = line.substringAfter(':').trim().equals("yes", true)
                line.startsWith("Dynamax Level:", true) -> dynamaxLevel = line.substringAfter(':').trim().toIntOrNull() ?: 10
                line.startsWith("Tera Type:", true) -> teraType = line.substringAfter(':').trim()
                line.startsWith("Poké Ball:", true) || line.startsWith("Pokeball:", true) -> pokeBall = line.substringAfter(':').trim()
                line.startsWith("EVs:", true) -> {
                    val evLine = line.substringAfter(':').trim()
                    Regex("\\(([^()]*)\\)\\s*$").find(evLine)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)?.let { nature = it }
                    evs = parseStatValues(evLine) { 0 }
                }
                line.startsWith("IVs:", true) -> ivs = parseStatValues(line.substringAfter(':')) { 31 }
                line.startsWith("-") -> moves += line.removePrefix("-").trim()
            }
        }
        return ShowdownTeamSet(
            nickname = nickname,
            species = species,
            item = item,
            ability = ability,
            moves = moves,
            nature = nature,
            evs = evs,
            gender = gender,
            ivs = ivs,
            shiny = shiny,
            level = level,
            happiness = happiness,
            pokeBall = pokeBall,
            hiddenPowerType = hiddenPowerType,
            gigantamax = gigantamax,
            dynamaxLevel = dynamaxLevel,
            teraType = teraType
        )
    }

    private fun parseStatValues(value: String, default: () -> Int): List<Int> {
        val names = mapOf("HP" to 0, "Atk" to 1, "Def" to 2, "SpA" to 3, "SpD" to 4, "Spe" to 5)
        val values = MutableList(6) { default() }
        value.split('/').forEach { part ->
            val match = Regex("^(\\d+\\+?|[-+])\\s+(.+)$").matchEntire(part.substringBefore('(').trim()) ?: return@forEach
            val index = names[match.groupValues[2].trim()] ?: return@forEach
            values[index] = match.groupValues[1].removeSuffix("+").toIntOrNull() ?: 0
        }
        return values
    }

    private fun textSet(set: ShowdownTeamSet): String {
        val subject = when {
            set.nickname.isNotBlank() && set.species.isNotBlank() && !set.nickname.equals(set.species, true) -> "${set.nickname.trim()} (${set.species.trim()})"
            set.species.isNotBlank() -> set.species.trim()
            else -> set.nickname.trim()
        }
        val gender = set.gender.trim().uppercase().takeIf { it == "M" || it == "F" }?.let { " ($it)" }.orEmpty()
        val header = buildString {
            append(subject)
            append(gender)
            if (set.item.isNotBlank()) append(" @ ${set.item.trim()}")
        }
        val lines = mutableListOf(header)
        if (set.ability.isNotBlank()) lines += "Ability: ${set.ability.trim()}"
        if (set.level != 100) lines += "Level: ${set.level.coerceIn(1, 100)}"
        if (set.shiny) lines += "Shiny: Yes"
        if (set.happiness != 255) lines += "Happiness: ${set.happiness.coerceIn(0, 255)}"
        val evText = formatStatValues(set.evs, 0)
        if (evText.isNotBlank()) lines += "EVs: $evText"
        if (set.nature.isNotBlank()) lines += "${set.nature.trim()} Nature"
        if (set.pokeBall.isNotBlank()) lines += "Poké Ball: ${set.pokeBall.trim()}"
        val ivText = formatStatValues(set.ivs, 31)
        if (ivText.isNotBlank()) lines += "IVs: $ivText"
        if (set.hiddenPowerType.isNotBlank()) lines += "Hidden Power: ${set.hiddenPowerType.trim()}"
        if (set.gigantamax) lines += "Gigantamax: Yes"
        if (set.dynamaxLevel != 10) lines += "Dynamax Level: ${set.dynamaxLevel.coerceIn(0, 10)}"
        if (set.teraType.isNotBlank()) lines += "Tera Type: ${set.teraType.trim()}"
        set.moves.take(4).mapTo(lines) { "- ${it.trim()}" }
        return lines.joinToString("\n")
    }

    private fun formatStatValues(values: List<Int>, default: Int): String {
        val names = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
        return values.mapIndexedNotNull { index, value -> value.takeUnless { it == default }?.let { "$it ${names[index]}" } }.joinToString(" / ")
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
