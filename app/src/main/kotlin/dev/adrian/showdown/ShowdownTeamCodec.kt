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
    val teraType: String = "",
    val malformed: Boolean = false
)

object ShowdownTeamCodec {
    private const val INVALID_NUMBER = -1
    const val MAX_TEAM_SIZE = 24
    private const val SHOWDOWN_ENGINE_MAX_MOVES_PER_SET = 24
    private const val SHOWDOWN_ENGINE_MAX_LEVEL = 99999

    private data class PackedAdvanced(
        val happiness: Int = 255,
        val pokeBall: String = "",
        val hiddenPowerType: String = "",
        val gigantamax: Boolean = false,
        val dynamaxLevel: Int = 10,
        val teraType: String = "",
        val malformed: Boolean = false
    )

    private data class ParsedStatValues(val values: List<Int>, val malformed: Boolean)

    private val hiddenPowerTypeIds = setOf(
        "bug",
        "dark",
        "dragon",
        "electric",
        "fairy",
        "fighting",
        "fire",
        "flying",
        "ghost",
        "grass",
        "ground",
        "ice",
        "normal",
        "poison",
        "psychic",
        "rock",
        "steel",
        "stellar",
        "water"
    )

    private val pokeBallIds = setOf(
        "beastball",
        "cherishball",
        "diveball",
        "dreamball",
        "duskball",
        "fastball",
        "featherball",
        "friendball",
        "gigatonball",
        "greatball",
        "healball",
        "heavyball",
        "jetball",
        "levelball",
        "loveball",
        "lureball",
        "luxuryball",
        "masterball",
        "moonball",
        "nestball",
        "netball",
        "originball",
        "parkball",
        "pokeball",
        "premierball",
        "quickball",
        "repeatball",
        "safariball",
        "sportball",
        "strangeball",
        "timerball",
        "ultraball",
        "wingball"
    )

    fun validate(sets: List<ShowdownTeamSet>): List<String> = validateSetFields(sets)

    fun validateImport(sets: List<ShowdownTeamSet>): List<String> = validate(sets)

    private fun validateSetFields(
        sets: List<ShowdownTeamSet>
    ): MutableList<String> {
        val errors = mutableListOf<String>()
        val populated = sets.filter { it.hasContent() }
        if (populated.isEmpty()) return mutableListOf("Add at least one Pokémon to the team.")
        if (populated.size > MAX_TEAM_SIZE) errors += "A team can contain at most $MAX_TEAM_SIZE Pokémon."
        populated.forEachIndexed { index, set ->
            val label = "Pokémon ${index + 1}"
            if (set.species.isBlank() && set.nickname.isBlank()) errors += "$label needs a species."
            if (set.malformed) errors += "$label contains malformed fields."
            if (set.moves.size > SHOWDOWN_ENGINE_MAX_MOVES_PER_SET) {
                errors += "$label can have at most $SHOWDOWN_ENGINE_MAX_MOVES_PER_SET moves."
            }
            if (set.evs.size != 6) errors += "$label has invalid EVs."
            if (set.ivs.size != 6) errors += "$label has invalid IVs."
            if (set.level !in 0..SHOWDOWN_ENGINE_MAX_LEVEL) errors += "$label has an invalid level."
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
            '|' in input -> unpack(input)
            else -> parseText(input)
        }
    }

    fun parseImport(value: String): List<ShowdownTeamSet> {
        val input = value.trim()
        if (input.isBlank()) return emptyList()
        if ('|' !in input) return parse(input)
        if (input.startsWith("[") || input.startsWith("{")) return parse(input)
        return if (input.split(']').all { it.split('|').size >= 12 }) {
            unpack(input)
        } else {
            listOf(ShowdownTeamSet(level = INVALID_NUMBER))
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
        val rawFields = packed.split('|')
        if (rawFields.size > 12 && rawFields.drop(12).any(String::isNotBlank)) return ShowdownTeamSet(level = INVALID_NUMBER, malformed = true)
        val fields = rawFields.take(12)
        val advanced = unpackAdvanced(fields.value(11))
        val shinyValue = fields.value(9)
        val evsValue = fields.value(6)
        val ivsValue = fields.value(8)
        val levelValue = fields.value(10)
        return ShowdownTeamSet(
            nickname = fields.value(0),
            species = fields.value(1).ifBlank { fields.value(0) },
            item = fields.value(2),
            ability = fields.value(3),
            moves = fields.value(4).split(',').filter(String::isNotBlank),
            nature = fields.value(5),
            evs = parseValues(evsValue, 0),
            gender = fields.value(7),
            ivs = parseValues(ivsValue, 31),
            shiny = shinyValue == "S",
            level = parseOptionalInt(levelValue, 100),
            happiness = advanced.happiness,
            pokeBall = advanced.pokeBall,
            hiddenPowerType = advanced.hiddenPowerType,
            gigantamax = advanced.gigantamax,
            dynamaxLevel = advanced.dynamaxLevel,
            teraType = advanced.teraType,
            malformed = advanced.malformed ||
                shinyValue.isNotBlank() && shinyValue != "S" ||
                hasMalformedOptionalInt(levelValue) ||
                hasMalformedPackedStatValues(evsValue) ||
                hasMalformedPackedStatValues(ivsValue)
        )
    }

    private fun unpackAdvanced(value: String): PackedAdvanced {
        if (value.isBlank()) return PackedAdvanced()
        val rawValues = value.split(',')
        val values = rawValues.take(6)
        val legacyOrder = isLegacyAdvancedOrder(values)
        val pokeBallIndex = if (legacyOrder) 1 else 2
        val hiddenPowerIndex = if (legacyOrder) 2 else 1
        return PackedAdvanced(
            happiness = parseOptionalInt(values.value(0), 255),
            pokeBall = values.value(pokeBallIndex),
            hiddenPowerType = values.value(hiddenPowerIndex),
            gigantamax = values.value(3) == "G",
            dynamaxLevel = parseOptionalInt(values.value(4), 10),
            teraType = values.value(5),
            malformed = rawValues.drop(6).any(String::isNotBlank) ||
                values.value(3).isNotBlank() && values.value(3) != "G" ||
                hasMalformedOptionalInt(values.value(0)) ||
                hasMalformedOptionalInt(values.value(4))
        )
    }

    private fun isLegacyAdvancedOrder(values: List<String>): Boolean {
        val firstAdvancedValue = values.value(1).lowercase()
        val secondAdvancedValue = values.value(2).lowercase()
        return when {
            firstAdvancedValue in hiddenPowerTypeIds -> false
            secondAdvancedValue in hiddenPowerTypeIds -> true
            firstAdvancedValue.isBlank() -> false
            secondAdvancedValue.isBlank() -> true
            firstAdvancedValue in pokeBallIds -> true
            else -> false
        }
    }

    private fun packSet(set: ShowdownTeamSet): String {
        val speciesName = set.species.trim().ifBlank { set.nickname.trim() }
        val displayName = set.nickname.trim().ifBlank { speciesName }
        val species = speciesName.takeUnless { packedId(displayName) == packedId(it) }.orEmpty()
        val fields = listOf(
            displayName,
            species,
            packedId(set.item),
            packedAbility(set.ability),
            set.moves.map(::packedId).filter(String::isNotBlank).joinToString(","),
            set.nature.trim(),
            packValues(set.evs, 0),
            set.gender.trim().uppercase().takeIf { it in setOf("M", "F", "N") }.orEmpty(),
            packValues(set.ivs, 31),
            if (set.shiny) "S" else "",
            set.level.takeUnless { it == 100 }?.toString().orEmpty(),
            packAdvanced(set)
        )
        return fields.joinToString("|")
    }

    private fun packAdvanced(set: ShowdownTeamSet): String {
        val values = listOf(
            set.happiness.takeUnless { it == 255 }?.toString().orEmpty(),
            set.hiddenPowerType.trim(),
            packedId(set.pokeBall),
            if (set.gigantamax) "G" else "",
            set.dynamaxLevel.takeUnless { it == 10 }?.toString().orEmpty(),
            set.teraType.trim()
        )
        return values.joinToString(",").trimEnd(',')
    }

    private fun parseText(input: String): List<ShowdownTeamSet> {
        val blocks = mutableListOf<String>()
        val current = mutableListOf<String>()
        fun flush() {
            if (current.any { it.isNotBlank() }) blocks += current.joinToString("\n")
            current.clear()
        }
        input.lineSequence().forEach { line ->
            if (line.trim().isBlank() || line.trim() == "---") flush() else current += line
        }
        flush()
        return blocks.mapNotNull(::parseTextSet)
    }

    private fun parseJson(input: String): List<ShowdownTeamSet> = runCatching {
        val values = if (input.startsWith("[")) JSONArray(input) else JSONArray().put(JSONObject(input))
        buildList {
            for (index in 0 until values.length()) {
                values.optJSONObject(index)?.let(::parseJsonSet)?.let { set ->
                    add(set.takeIf { it.hasContent() } ?: ShowdownTeamSet(level = INVALID_NUMBER))
                } ?: add(ShowdownTeamSet(level = INVALID_NUMBER))
            }
        }
    }.getOrDefault(emptyList())

    private fun parseJsonSet(value: JSONObject): ShowdownTeamSet {
        if (hasInvalidJsonTypes(value)) return ShowdownTeamSet(malformed = true)
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
            level = jsonInt(value, "level", 100),
            happiness = jsonInt(value, "happiness", 255),
            pokeBall = value.optString("pokeball", value.optString("pokeBall")),
            hiddenPowerType = value.optString(
                "hiddenpowertype",
                value.optString(
                    "hpType",
                    value.optString("hiddenpower", value.optString("hiddenPower"))
                )
            ),
            gigantamax = value.optBoolean("gigantamax"),
            dynamaxLevel = when {
                value.has("dynamaxlevel") -> jsonInt(value, "dynamaxlevel", 10)
                else -> jsonInt(value, "dynamaxLevel", 10)
            },
            teraType = value.optString("teratype", value.optString("teraType")),
            malformed = hasMalformedJsonNumbers(value)
        )
    }

    private fun hasInvalidJsonTypes(value: JSONObject): Boolean {
        val textFields = listOf(
            "name",
            "species",
            "item",
            "ability",
            "nature",
            "gender",
            "pokeball",
            "pokeBall",
            "hiddenpowertype",
            "hpType",
            "hiddenpower",
            "hiddenPower",
            "teratype",
            "teraType"
        )
        if (textFields.any { value.has(it) && value.opt(it) !is String }) return true
        if (value.has("moves")) {
            val moves = value.optJSONArray("moves") ?: return true
            if ((0 until moves.length()).any { index ->
                    val move = moves.opt(index)
                    move !is String || move.isBlank()
                }) return true
        }
        if (listOf("evs", "ivs").any { value.has(it) && value.optJSONObject(it) == null }) return true
        if (listOf("shiny", "gigantamax").any { value.has(it) && value.opt(it) !is Boolean }) return true
        return false
    }

    private fun hasMalformedJsonNumbers(value: JSONObject): Boolean {
        val numericFields = listOf("level", "happiness", "dynamaxlevel", "dynamaxLevel")
        if (numericFields.any { value.has(it) && !isJsonInteger(value.opt(it)) }) return true
        val statNames = listOf("hp", "atk", "def", "spa", "spd", "spe")
        return listOf("evs", "ivs").any { name ->
                val stats = value.optJSONObject(name) ?: return@any false
                statNames.any { stats.has(it) && !isJsonInteger(stats.opt(it)) }
            }
    }

    private fun isJsonInteger(value: Any?): Boolean = when (value) {
        is Number -> {
            val numeric = value.toDouble()
            numeric.isFinite() && numeric % 1.0 == 0.0 && numeric >= Int.MIN_VALUE && numeric <= Int.MAX_VALUE
        }
        is String -> value.trim().toIntOrNull() != null
        else -> false
    }

    private fun jsonStatValues(value: JSONObject?, default: Int): List<Int> {
        val names = listOf("hp", "atk", "def", "spa", "spd", "spe")
        return names.map { name -> value?.let { jsonInt(it, name, default) } ?: default }
    }

    private fun jsonInt(value: JSONObject, name: String, default: Int): Int {
        if (!value.has(name)) return default
        return when (val raw = value.opt(name)) {
            is Number -> {
                val numeric = raw.toDouble()
                if (!numeric.isFinite() || numeric % 1.0 != 0.0 || numeric < Int.MIN_VALUE || numeric > Int.MAX_VALUE) {
                    INVALID_NUMBER
                } else {
                    numeric.toInt()
                }
            }
            is String -> raw.trim().toIntOrNull() ?: INVALID_NUMBER
            else -> INVALID_NUMBER
        }
    }

    private fun jsonSet(set: ShowdownTeamSet) = JSONObject().apply {
        if (set.nickname.isNotBlank()) put("name", set.nickname.trim())
        if (set.species.isNotBlank()) put("species", set.species.trim())
        if (set.item.isNotBlank()) put("item", set.item.trim())
        if (set.ability.isNotBlank()) put("ability", set.ability.trim())
        if (set.moves.isNotEmpty()) put("moves", JSONArray(set.moves))
        if (set.nature.isNotBlank()) put("nature", set.nature.trim())
        put("evs", jsonStats(set.evs, 0))
        if (set.gender.isNotBlank()) put("gender", set.gender.trim())
        put("ivs", jsonStats(set.ivs, 31))
        if (set.shiny) put("shiny", true)
        if (set.level != 100) put("level", set.level)
        if (set.happiness != 255) put("happiness", set.happiness)
        if (set.pokeBall.isNotBlank()) put("pokeball", set.pokeBall.trim())
        if (set.hiddenPowerType.isNotBlank()) put("hpType", set.hiddenPowerType.trim())
        if (set.gigantamax) put("gigantamax", true)
        if (set.dynamaxLevel != 10) put("dynamaxLevel", set.dynamaxLevel)
        if (set.teraType.isNotBlank()) put("teraType", set.teraType.trim())
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
        val gender = Regex("\\s\\(([MFN])\\)$").find(subject)?.groupValues?.get(1).orEmpty()
        val withoutGender = subject.replace(Regex("\\s\\([MFN]\\)$"), "").trim()
        val speciesMatch = Regex("^(.+) \\(([^()]*)\\)$").matchEntire(withoutGender)
        val nickname = speciesMatch?.groupValues?.get(1).orEmpty()
        val species = speciesMatch?.groupValues?.get(2).orEmpty().ifBlank { withoutGender }
        val moves = mutableListOf<String>()
        var ability = ""
        var nature = ""
        var level = 100
        var happiness = 255
        var happinessSpecified = false
        var shiny = false
        var pokeBall = ""
        var hiddenPowerType = ""
        var gigantamax = false
        var dynamaxLevel = 10
        var teraType = ""
        var malformed = false
        var evs = List(6) { 0 }
        var ivs = List(6) { 31 }
        lines.drop(1).forEach { line ->
            val betaAbility = Regex("^\\[([^\\]]+)](?:\\s*@\\s*(.*))?$").matchEntire(line)
            when {
                betaAbility != null -> {
                    ability = betaAbility.groupValues[1].trim()
                    item = betaAbility.groupValues.getOrNull(2).orEmpty().trim()
                }
                line.startsWith("Ability:", true) || line.startsWith("Trait:", true) -> ability = line.substringAfter(':').trim()
                line.endsWith(" Nature", true) -> nature = line.removeSuffix(" Nature").trim()
                line.startsWith("Level:", true) -> {
                    val parsed = line.substringAfter(':').trim().toIntOrNull()
                    if (parsed == null) {
                        malformed = true
                        level = INVALID_NUMBER
                    } else {
                        level = parsed
                    }
                }
                line.startsWith("Happiness:", true) -> {
                    happinessSpecified = true
                    val parsed = line.substringAfter(':').trim().toIntOrNull()
                    if (parsed == null) {
                        malformed = true
                        happiness = INVALID_NUMBER
                    } else {
                        happiness = parsed
                    }
                }
                line.startsWith("Shiny:", true) -> {
                    when (line.substringAfter(':').trim().lowercase()) {
                        "yes" -> shiny = true
                        "no" -> shiny = false
                        else -> malformed = true
                    }
                }
                line.startsWith("Hidden Power:", true) -> hiddenPowerType = line.substringAfter(':').trim()
                line.startsWith("Gigantamax:", true) -> {
                    when (line.substringAfter(':').trim().lowercase()) {
                        "yes" -> gigantamax = true
                        "no" -> gigantamax = false
                        else -> malformed = true
                    }
                }
                line.startsWith("Dynamax Level:", true) -> {
                    val parsed = line.substringAfter(':').trim().toIntOrNull()
                    if (parsed == null) {
                        malformed = true
                        dynamaxLevel = INVALID_NUMBER
                    } else {
                        dynamaxLevel = parsed
                    }
                }
                line.startsWith("Tera Type:", true) -> teraType = line.substringAfter(':').trim()
                line.startsWith("Poké Ball:", true) || line.startsWith("Pokeball:", true) -> pokeBall = line.substringAfter(':').trim()
                line.startsWith("EVs:", true) -> {
                    val evLine = line.substringAfter(':').trim()
                    Regex("\\(([^()]*)\\)\\s*$").find(evLine)?.groupValues?.get(1)?.trim()?.takeIf(String::isNotBlank)?.let { nature = it }
                    val parsed = parseStatValues(evLine) { 0 }
                    evs = parsed.values
                    malformed = malformed || parsed.malformed
                }
                line.startsWith("IVs:", true) -> {
                    val parsed = parseStatValues(line.substringAfter(':')) { 31 }
                    ivs = parsed.values
                    malformed = malformed || parsed.malformed
                }
                line.startsWith("-") || line.startsWith("~") -> {
                    val move = line.drop(1).trim()
                    moves += Regex("^Hidden Power \\[([^]]+)]$", RegexOption.IGNORE_CASE)
                        .replace(move) { "Hidden Power ${it.groupValues[1]}" }
                }
            }
        }
        if (!happinessSpecified && moves.any { ShowdownMoveDex.moveId(it) == "frustration" }) happiness = 0
        item = item.takeUnless { it.equals("No Item", true) }.orEmpty()
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
            teraType = teraType,
            malformed = malformed
        )
    }

    private fun parseStatValues(value: String, default: () -> Int): ParsedStatValues {
        val names = mapOf("HP" to 0, "Atk" to 1, "Def" to 2, "SpA" to 3, "SpD" to 4, "Spe" to 5)
        val values = MutableList(6) { default() }
        var malformed = false
        var invalidClause = false
        value.split('/').forEach { part ->
            val normalized = part.substringBefore('(').trim()
            if (normalized.isBlank()) {
                malformed = true
                invalidClause = true
                return@forEach
            }
            val stat = normalized.substringAfterLast(' ', "").trim()
            val index = names[stat]
            if (index == null) {
                malformed = true
                invalidClause = true
                return@forEach
            }
            val number = normalized.removeSuffix(stat).trim().removeSuffix("+")
            val parsed = if (number == "-") 0 else number.toIntOrNull()
            if (parsed == null) malformed = true
            values[index] = parsed ?: INVALID_NUMBER
        }
        if (value.trim().isBlank()) malformed = true
        if (invalidClause || value.trim().isBlank()) {
            values[0] = INVALID_NUMBER
        }
        return ParsedStatValues(values, malformed)
    }

    private fun textSet(set: ShowdownTeamSet): String {
        val subject = when {
            set.nickname.isNotBlank() && set.species.isNotBlank() && !set.nickname.equals(set.species, true) -> "${set.nickname.trim()} (${set.species.trim()})"
            set.species.isNotBlank() -> set.species.trim()
            else -> set.nickname.trim()
        }
        val gender = set.gender.trim().uppercase().takeIf { it in setOf("M", "F", "N") }?.let { " ($it)" }.orEmpty()
        val header = buildString {
            append(subject)
            append(gender)
            if (set.item.isNotBlank()) append(" @ ${set.item.trim()}")
        }
        val lines = mutableListOf(header)
        if (set.ability.isNotBlank()) lines += "Ability: ${set.ability.trim()}"
        if (set.level != 100) lines += "Level: ${set.level}"
        if (set.shiny) lines += "Shiny: Yes"
        if (set.happiness != 255) lines += "Happiness: ${set.happiness}"
        if (set.pokeBall.isNotBlank()) lines += "Pokeball: ${set.pokeBall.trim()}"
        if (set.hiddenPowerType.isNotBlank()) lines += "Hidden Power: ${set.hiddenPowerType.trim()}"
        if (set.dynamaxLevel != 10) lines += "Dynamax Level: ${set.dynamaxLevel}"
        if (set.gigantamax) lines += "Gigantamax: Yes"
        if (set.teraType.isNotBlank()) lines += "Tera Type: ${set.teraType.trim()}"
        val evText = formatStatValues(set.evs, 0)
        if (evText.isNotBlank()) lines += "EVs: $evText"
        if (set.nature.isNotBlank()) lines += "${set.nature.trim()} Nature"
        val ivText = formatStatValues(set.ivs, 31)
        if (ivText.isNotBlank()) lines += "IVs: $ivText"
        set.moves.mapTo(lines) { "- ${exportMoveName(it)}" }
        return lines.joinToString("\n")
    }

    private fun exportMoveName(value: String): String {
        val move = value.trim()
        val prefix = "Hidden Power "
        if (!move.startsWith(prefix, true)) return move
        val type = move.substring(prefix.length).trim().removePrefix("[").removeSuffix("]").trim()
        return if (type.isBlank()) move else "$prefix[$type]"
    }

    private fun formatStatValues(values: List<Int>, default: Int): String {
        val names = listOf("HP", "Atk", "Def", "SpA", "SpD", "Spe")
        return values.mapIndexedNotNull { index, value -> value.takeUnless { it == default }?.let { "$it ${names[index]}" } }.joinToString(" / ")
    }

    private fun packValues(values: List<Int>, default: Int): String {
        val normalized = (0 until 6).map { values.getOrNull(it) ?: default }
        if (normalized.all { it == default }) return ""
        return normalized.joinToString(",") { value -> value.takeUnless { it == default }?.toString().orEmpty() }
    }

    private fun parseValues(value: String, default: Int): List<Int> {
        if (value.isBlank()) return List(6) { default }
        return value.split(',', limit = 6).let { values ->
            (0 until 6).map { index ->
                val current = values.getOrNull(index).orEmpty().trim()
                if (current.isBlank()) default else current.toIntOrNull() ?: INVALID_NUMBER
            }
        }
    }

    private fun hasMalformedOptionalInt(value: String): Boolean =
        ShowdownTeamEditorValues.isMalformedNumber(value)

    private fun hasMalformedPackedStatValues(value: String): Boolean =
        value.isNotBlank() && value.split(',', limit = 6).any(ShowdownTeamEditorValues::isMalformedNumber)

    private fun parseOptionalInt(value: String, default: Int): Int {
        val normalized = value.trim()
        return if (normalized.isBlank()) default else normalized.toIntOrNull() ?: INVALID_NUMBER
    }

    private fun ShowdownTeamSet.hasContent(): Boolean {
        val hasText = listOf(nickname, species, item, ability, nature, gender, pokeBall, hiddenPowerType, teraType).any(String::isNotBlank)
        val hasStats = evs.any { it != 0 } || ivs.any { it != 31 }
        val hasDetails = shiny || gigantamax || level != 100 || happiness != 255 || dynamaxLevel != 10
        return malformed || hasText || moves.isNotEmpty() || hasStats || hasDetails
    }

    private fun packedId(value: String) = value.lowercase().filter(Char::isLetterOrDigit)

    private fun packedAbility(value: String) = when (value.trim().lowercase()) {
        "0" -> "0"
        "1" -> "1"
        "h" -> "H"
        "s" -> "S"
        else -> packedId(value)
    }

    private fun List<String>.value(index: Int) = getOrNull(index).orEmpty()
}
