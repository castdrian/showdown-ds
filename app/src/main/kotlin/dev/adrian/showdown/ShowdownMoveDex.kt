package dev.adrian.showdown

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.Locale

class ShowdownMoveDex(private val resourceCache: ShowdownSpriteCache) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val moveTypes = mutableMapOf<String, String>()
    private val moveInfo = mutableMapOf<String, BattleSession.MoveInfo>()
    private val pokemonTypes = mutableMapOf<String, List<String>>()
    private val pokemonAbilities = mutableMapOf<String, List<String>>()
    private val pokemonAbilitySlots = mutableMapOf<String, Map<String, String>>()
    private val pokemonMoves = mutableMapOf<String, List<String>>()
    private val moveNames = mutableListOf<String>()
    private val pokemonNames = mutableListOf<String>()
    private val itemNames = mutableListOf<String>()
    private val abilityNames = mutableListOf<String>()
    private val listeners = mutableListOf<() -> Unit>()
    private var loading = false
    private var loaded = false

    fun typeFor(move: String) = moveTypes[moveId(move)]

    fun infoFor(move: String) = moveInfo[moveId(move)]

    fun typesFor(species: String) = pokemonTypes[speciesId(species)]

    fun abilitiesFor(species: String) = pokemonAbilities[speciesId(species)]
        .orEmpty()
        .map { displayName(it, abilityNames) }

    fun movesFor(species: String) = pokemonMoves[speciesId(species)]
        .orEmpty()
        .map { displayName(it, moveNames) }

    fun moveNames() = moveNames.toList()

    fun pokemonNames() = pokemonNames.toList()

    fun itemNames() = itemNames.toList()

    fun abilityNames() = abilityNames.toList()

    fun moveNameFor(move: String) = displayName(move, moveNames)

    fun itemNameFor(item: String) = displayName(item, itemNames)

    fun abilityNameFor(ability: String) = displayName(ability, abilityNames)

    fun abilityFor(species: String, ability: String): String {
        val rawAbility = ability.trim()
        val resolvedAbility = pokemonAbilitySlots[speciesId(species)]?.get(rawAbility)
            ?: pokemonAbilitySlots[speciesId(species)]?.get(rawAbility.uppercase(Locale.ROOT))
            ?: rawAbility
        return displayName(resolvedAbility, abilityNames)
    }

    fun natureNames() = NATURE_NAMES

    fun load(listener: () -> Unit) {
        if (loaded) {
            listener()
            return
        }
        listeners += listener
        if (loading) return
        loading = true
        resourceCache.requestMoveDex { file ->
            if (executor.isShutdown) return@requestMoveDex
            resourceCache.requestPokedex { pokedexFile ->
                if (executor.isShutdown) return@requestPokedex
                resourceCache.requestItems { itemsFile ->
                    if (executor.isShutdown) return@requestItems
                    resourceCache.requestAbilities { abilitiesFile ->
                        if (executor.isShutdown) return@requestAbilities
                        resourceCache.requestLearnsets { learnsetsFile ->
                            if (executor.isShutdown) return@requestLearnsets
                            executor.execute {
                                val moveContents = file?.readText().orEmpty()
                                val pokemonContents = pokedexFile?.readText().orEmpty()
                                val itemContents = itemsFile?.readText().orEmpty()
                                val abilityContents = abilitiesFile?.readText().orEmpty()
                                val learnsetsContents = learnsetsFile?.readText().orEmpty()
                                val loadedMoveTypes = parseMoveTypes(moveContents)
                                val loadedMoveInfo = parseMoveInfo(moveContents)
                                val loadedPokemonTypes = parsePokemonTypes(pokemonContents)
                                val loadedPokemonAbilities = parsePokemonAbilities(pokemonContents)
                                val loadedPokemonAbilitySlots = parsePokemonAbilitySlots(pokemonContents)
                                val loadedPokemonMoves = parseLearnsets(learnsetsContents)
                                val loadedMoveNames = parseMoveNames(moveContents)
                                val loadedPokemonNames = parsePokemonNames(pokemonContents)
                                val loadedItemNames = parseScriptNames(itemContents)
                                val loadedAbilityNames = parseScriptNames(abilityContents)
                                mainHandler.post {
                                    loading = false
                                    loaded = true
                                    moveTypes.putAll(loadedMoveTypes)
                                    moveInfo.putAll(loadedMoveInfo)
                                    pokemonTypes.putAll(loadedPokemonTypes)
                                    pokemonAbilities.putAll(loadedPokemonAbilities)
                                    pokemonAbilitySlots.putAll(loadedPokemonAbilitySlots)
                                    pokemonMoves.putAll(loadedPokemonMoves)
                                    moveNames.clear()
                                    moveNames += loadedMoveNames
                                    pokemonNames.clear()
                                    pokemonNames += loadedPokemonNames
                                    itemNames.clear()
                                    itemNames += loadedItemNames
                                    abilityNames.clear()
                                    abilityNames += loadedAbilityNames
                                    val callbacks = listeners.toList()
                                    listeners.clear()
                                    callbacks.forEach { it() }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
        listeners.clear()
    }

    companion object {
        fun parseMoveTypes(contents: String): Map<String, String> {
            return runCatching {
                val moves = JSONObject(contents)
                buildMap {
                    moves.keys().forEach { id ->
                        moves.optJSONObject(id)?.optString("type")?.uppercase()?.takeIf { it.isNotBlank() }?.let { put(id, it) }
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun parseMoveInfo(contents: String): Map<String, BattleSession.MoveInfo> {
            return runCatching {
                val moves = JSONObject(contents)
                buildMap {
                    moves.keys().forEach { id ->
                        val move = moves.optJSONObject(id) ?: return@forEach
                        val power = move.optInt("basePower", 0).takeIf { it > 0 }?.toString()
                            ?: "—"
                        val accuracy = when (val value = move.opt("accuracy")) {
                            is Number -> value.toString().removeSuffix(".0")
                            else -> "—"
                        }
                        val category = move.optString("category").trim().takeIf { it.isNotBlank() } ?: "Status"
                        val isZMove = move.opt("isZ")?.let { it != JSONObject.NULL && it != false } == true
                        val maxMode = move.opt("isMax")
                        val isFixedGimmickPower = (isZMove && power.toIntOrNull()?.let { it > 1 } == true) ||
                            (maxMode is String && power.toIntOrNull()?.let { it > 10 } == true)
                        put(id, BattleSession.MoveInfo(power, accuracy, category, isFixedGimmickPower))
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun parsePokemonTypes(contents: String): Map<String, List<String>> {
            return runCatching {
                val pokemon = JSONObject(contents)
                buildMap {
                    pokemon.keys().forEach { id ->
                        val types = pokemon.optJSONObject(id)?.optJSONArray("types") ?: return@forEach
                        val parsed = buildList {
                            for (index in 0 until types.length()) {
                                types.optString(index).uppercase(Locale.ROOT).takeIf { it.isNotBlank() }?.let(::add)
                            }
                        }
                        if (parsed.isNotEmpty()) put(id, parsed)
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun parsePokemonAbilities(contents: String): Map<String, List<String>> {
            return runCatching {
                val pokemon = JSONObject(contents)
                buildMap {
                    pokemon.keys().forEach { id ->
                        val abilities = pokemon.optJSONObject(id)?.optJSONObject("abilities") ?: return@forEach
                        val parsed = abilities.keys().asSequence()
                            .sortedWith(compareBy<String> { it != "0" }.thenBy { it })
                            .mapNotNull { key ->
                                abilities.optString(key).trim().takeIf { it.isNotBlank() }?.let(::moveId)
                            }
                            .distinct()
                            .toList()
                        if (parsed.isNotEmpty()) put(id, parsed)
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun parsePokemonAbilitySlots(contents: String): Map<String, Map<String, String>> {
            return runCatching {
                val pokemon = JSONObject(contents)
                buildMap {
                    pokemon.keys().forEach { id ->
                        val abilities = pokemon.optJSONObject(id)?.optJSONObject("abilities") ?: return@forEach
                        val slots = buildMap {
                            abilities.keys().forEach { slot ->
                                abilities.optString(slot).trim().takeIf { it.isNotBlank() }?.let { put(slot, moveId(it)) }
                            }
                        }
                        if (slots.isNotEmpty()) put(id, slots)
                    }
                }
            }.getOrDefault(emptyMap())
        }

        fun parseLearnsets(contents: String): Map<String, List<String>> {
            if (contents.isBlank()) return emptyMap()
            val speciesPattern = Regex("""(?s)([a-z0-9]+):\{learnset:\{(.*?)\}\}""")
            val movePattern = Regex("""([a-z0-9]+):\[""")
            return speciesPattern.findAll(contents).mapNotNull { speciesMatch ->
                val moves = movePattern.findAll(speciesMatch.groupValues[2])
                    .map { it.groupValues[1] }
                    .distinct()
                    .sorted()
                    .toList()
                moves.takeIf { it.isNotEmpty() }?.let { speciesMatch.groupValues[1] to it }
            }.toMap()
        }

        fun parseMoveNames(contents: String): List<String> = parseNames(contents)

        fun parsePokemonNames(contents: String): List<String> = parseNames(contents)

        fun typeNames() = HIDDEN_POWER_TYPE_NAMES

        fun teraTypeNames() = TERA_TYPE_NAMES

        fun parseScriptNames(contents: String): List<String> = Regex("name:\"((?:\\\\.|[^\"])*)\"")
            .findAll(contents)
            .map { it.groupValues[1].replace("\\\\\"", "\"") }
            .filter(String::isNotBlank)
            .distinct()
            .sorted()
            .toList()

        private fun parseNames(contents: String): List<String> {
            return runCatching {
                val values = JSONObject(contents)
                buildList {
                    values.keys().forEach { id ->
                        values.optJSONObject(id)?.optString("name")?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }.distinct().sorted()
            }.getOrDefault(emptyList())
        }

        fun moveId(move: String) = move.lowercase().filter(Char::isLetterOrDigit)

        fun speciesId(species: String) = species.lowercase(Locale.ROOT)
            .replace("♀", "f")
            .replace("♂", "m")
            .filter(Char::isLetterOrDigit)

        private fun displayName(value: String, names: List<String>) =
            names.firstOrNull { moveId(it) == moveId(value) } ?: value

        private val NATURE_NAMES = listOf(
            "Adamant", "Bashful", "Bold", "Brave", "Calm", "Careful", "Docile", "Gentle", "Hardy", "Hasty",
            "Impish", "Jolly", "Lax", "Lonely", "Mild", "Modest", "Naive", "Naughty", "Quiet", "Quirky",
            "Rash", "Relaxed", " sassy", "Serious", "Timid"
        ).map(String::trim)

        private val TERA_TYPE_NAMES = listOf(
            "Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass",
            "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water", "Stellar"
        )

        private val HIDDEN_POWER_TYPE_NAMES = TERA_TYPE_NAMES.filterNot { it == "Fairy" || it == "Normal" || it == "Stellar" }
    }
}
