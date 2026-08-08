package com.showdown.ds

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.Locale

class ShowdownMoveDex(private val resourceCache: ShowdownSpriteCache) : AutoCloseable {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val executor = Executors.newSingleThreadExecutor()
    private val moveTypes = mutableMapOf<String, String>()
    private val pokemonTypes = mutableMapOf<String, List<String>>()
    private val moveNames = mutableListOf<String>()
    private val pokemonNames = mutableListOf<String>()
    private val itemNames = mutableListOf<String>()
    private val abilityNames = mutableListOf<String>()
    private val listeners = mutableListOf<() -> Unit>()
    private var loading = false

    fun typeFor(move: String) = moveTypes[moveId(move)]

    fun typesFor(species: String) = pokemonTypes[speciesId(species)]

    fun moveNames() = moveNames.toList()

    fun pokemonNames() = pokemonNames.toList()

    fun itemNames() = itemNames.toList()

    fun abilityNames() = abilityNames.toList()

    fun natureNames() = NATURE_NAMES

    fun typeNames() = TYPE_NAMES

    fun load(listener: () -> Unit) {
        if (moveTypes.isNotEmpty() && pokemonTypes.isNotEmpty() && moveNames.isNotEmpty() && pokemonNames.isNotEmpty() && itemNames.isNotEmpty() && abilityNames.isNotEmpty()) {
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
                        executor.execute {
                            val moveContents = file?.readText().orEmpty()
                            val pokemonContents = pokedexFile?.readText().orEmpty()
                            val itemContents = itemsFile?.readText().orEmpty()
                            val abilityContents = abilitiesFile?.readText().orEmpty()
                            val loadedMoveTypes = parseMoveTypes(moveContents)
                            val loadedPokemonTypes = parsePokemonTypes(pokemonContents)
                            val loadedMoveNames = parseMoveNames(moveContents)
                            val loadedPokemonNames = parsePokemonNames(pokemonContents)
                            val loadedItemNames = parseScriptNames(itemContents)
                            val loadedAbilityNames = parseScriptNames(abilityContents)
                            mainHandler.post {
                                loading = false
                                moveTypes.putAll(loadedMoveTypes)
                                pokemonTypes.putAll(loadedPokemonTypes)
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

        fun parseMoveNames(contents: String): List<String> = parseNames(contents)

        fun parsePokemonNames(contents: String): List<String> = parseNames(contents)

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

        private val NATURE_NAMES = listOf(
            "Adamant", "Bashful", "Bold", "Brave", "Calm", "Careful", "Docile", "Gentle", "Hardy", "Hasty",
            "Impish", "Jolly", "Lax", "Lonely", "Mild", "Modest", "Naive", "Naughty", "Quiet", "Quirky",
            "Rash", "Relaxed", " sassy", "Serious", "Timid"
        ).map(String::trim)

        private val TYPE_NAMES = listOf(
            "Bug", "Dark", "Dragon", "Electric", "Fairy", "Fighting", "Fire", "Flying", "Ghost", "Grass",
            "Ground", "Ice", "Normal", "Poison", "Psychic", "Rock", "Steel", "Water"
        )
    }
}
