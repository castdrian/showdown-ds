package dev.adrian.showdown

import org.json.JSONObject

internal object PokeApiSpriteDexNumbers {
    fun parse(contents: String): Map<String, Int> = runCatching {
        val root = JSONObject(contents)
        buildMap {
            root.keys().forEach { speciesId ->
                val number = root.optJSONObject(speciesId)?.optInt("num", 0)?.takeIf { it > 0 } ?: return@forEach
                put(ShowdownAssetPaths.animationId(speciesId), number)
            }
        }
    }.getOrDefault(emptyMap())

    fun resolve(speciesNames: List<String>, numbers: Map<String, Int>): Int? =
        speciesNames.firstNotNullOfOrNull { species -> numbers[ShowdownAssetPaths.animationId(species)] }
}
