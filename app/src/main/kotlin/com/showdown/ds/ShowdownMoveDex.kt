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
    private val listeners = mutableListOf<() -> Unit>()
    private var loading = false

    fun typeFor(move: String) = moveTypes[moveId(move)]

    fun typesFor(species: String) = pokemonTypes[speciesId(species)]

    fun load(listener: () -> Unit) {
        if (moveTypes.isNotEmpty() && pokemonTypes.isNotEmpty()) {
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
                executor.execute {
                    val loadedMoveTypes = file?.readText()?.let(::parseMoveTypes).orEmpty()
                    val loadedPokemonTypes = pokedexFile?.readText()?.let(::parsePokemonTypes).orEmpty()
                    mainHandler.post {
                        loading = false
                        moveTypes.putAll(loadedMoveTypes)
                        pokemonTypes.putAll(loadedPokemonTypes)
                        val callbacks = listeners.toList()
                        listeners.clear()
                        callbacks.forEach { it() }
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
            val moves = JSONObject(contents)
            return buildMap {
                moves.keys().forEach { id ->
                    moves.optJSONObject(id)?.optString("type")?.uppercase()?.takeIf { it.isNotBlank() }?.let { put(id, it) }
                }
            }
        }

        fun parsePokemonTypes(contents: String): Map<String, List<String>> {
            val pokemon = JSONObject(contents)
            return buildMap {
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
        }

        fun moveId(move: String) = move.lowercase().filter(Char::isLetterOrDigit)

        fun speciesId(species: String) = species.lowercase(Locale.ROOT)
            .replace("♀", "f")
            .replace("♂", "m")
            .filter(Char::isLetterOrDigit)
    }
}
