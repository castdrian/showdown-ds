package com.showdown.ds

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.util.Locale
import java.util.concurrent.Executors

class ShowdownPokedex(initialEntries: List<ShowdownPokedex.Entry> = emptyList()) : AutoCloseable {
    data class Entry(
        val id: String,
        val name: String,
        val number: Int?,
        val types: List<String>,
        val abilities: List<String>,
        val baseStats: Map<String, Int>,
        val heightMeters: Double?,
        val weightKg: Double?,
        val tier: String,
        val generation: Int?,
        val color: String,
        val eggGroups: List<String>,
        val preEvolution: String?,
        val evolutions: List<String>
    )

    private val executor = Executors.newSingleThreadExecutor()
    private var values: List<Entry> = initialEntries

    val isLoaded get() = values.isNotEmpty()

    fun entries() = values

    fun search(query: String, limit: Int = 24): List<Entry> {
        val normalized = normalize(query)
        if (normalized.isBlank()) return emptyList()
        return values.asSequence()
            .filter { normalize(it.name).contains(normalized) || it.id.contains(normalized) }
            .sortedWith(compareBy<Entry> { normalize(it.name) != normalized }.thenBy { normalize(it.name).startsWith(normalized).not() }.thenBy { it.number ?: Int.MAX_VALUE }.thenBy { it.name })
            .take(limit)
            .toList()
    }

    fun find(idOrName: String): Entry? {
        val normalized = normalize(idOrName)
        return values.firstOrNull { it.id == normalized || normalize(it.name) == normalized }
    }

    fun load(contents: String, listener: () -> Unit) {
        if (executor.isShutdown) return
        val mainHandler = Handler(Looper.getMainLooper())
        executor.execute {
            val parsed = parse(contents)
            mainHandler.post {
                if (executor.isShutdown) return@post
                values = parsed
                listener()
            }
        }
    }

    override fun close() {
        executor.shutdownNow()
    }

    companion object {
        fun parse(contents: String): List<Entry> {
            return runCatching {
                val root = JSONObject(contents)
                buildList {
                    root.keys().forEach { id ->
                        val value = root.optJSONObject(id) ?: return@forEach
                        val name = value.optString("name").trim().takeIf { it.isNotBlank() } ?: return@forEach
                        add(
                            Entry(
                                id = id,
                                name = name,
                                number = value.optInt("num", 0).takeIf { it != 0 },
                                types = value.optJSONArray("types").toStringList(),
                                abilities = value.optJSONObject("abilities").stringValues(),
                                baseStats = value.optJSONObject("baseStats").intValues(),
                                heightMeters = value.optDouble("heightm", Double.NaN).takeUnless(Double::isNaN),
                                weightKg = value.optDouble("weightkg", Double.NaN).takeUnless(Double::isNaN),
                                tier = value.optString("tier").trim(),
                                generation = value.optInt("gen", 0).takeIf { it != 0 },
                                color = value.optString("color").trim(),
                                eggGroups = value.optJSONArray("eggGroups").toStringList(),
                                preEvolution = value.optString("prevo").trim().takeIf { it.isNotBlank() },
                                evolutions = value.optJSONArray("evos").toStringList()
                            )
                        )
                    }
                }.sortedWith(compareBy<Entry> { it.number ?: Int.MAX_VALUE }.thenBy { it.name })
            }.getOrDefault(emptyList())
        }

        private fun normalize(value: String) = value.lowercase(Locale.ROOT)
            .replace("♀", "f")
            .replace("♂", "m")
            .filter(Char::isLetterOrDigit)

        private fun org.json.JSONArray?.toStringList(): List<String> {
            if (this == null) return emptyList()
            return buildList {
                for (index in 0 until length()) {
                    optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
                }
            }
        }

        private fun JSONObject?.stringValues(): List<String> {
            if (this == null) return emptyList()
            return keys().asSequence().sorted().mapNotNull { optString(it).trim().takeIf(String::isNotBlank) }.toList()
        }

        private fun JSONObject?.intValues(): Map<String, Int> {
            if (this == null) return emptyMap()
            return keys().asSequence().mapNotNull { key ->
                optInt(key, Int.MIN_VALUE).takeIf { it != Int.MIN_VALUE }?.let { key to it }
            }.toMap()
        }
    }
}
