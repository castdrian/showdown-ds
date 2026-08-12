package dev.adrian.showdown

import java.util.Locale

object ShowdownAssetPaths {
    fun battleSprite(species: String, back: Boolean, style: BattleSession.SpriteStyle): String {
        val collection = if (style == BattleSession.SpriteStyle.MODERN_3D) "xyani" else "gen5ani"
        return "sprites/${if (back) "$collection-back" else collection}/${animationId(species)}.gif"
    }

    fun battleSpriteCandidates(species: String, back: Boolean, style: BattleSession.SpriteStyle): List<String> {
        val candidates = linkedSetOf<String>()
        val baseSpecies = species.substringBefore('-').trim()
        val speciesNames = buildList {
            add(species)
            if (baseSpecies.isNotEmpty() && !baseSpecies.equals(species.trim(), ignoreCase = true)) add(baseSpecies)
        }
        val collections = buildList {
            add(if (style == BattleSession.SpriteStyle.MODERN_3D) "xyani" else "gen5ani")
            if (style == BattleSession.SpriteStyle.MODERN_3D) add("gen5ani")
        }
        if (back) {
            trueBackSpritePaths(species).forEach { candidates += it }
        }
        collections.forEach { collection ->
            speciesNames.forEach { name -> candidates += battleSprite(name, back, collection) }
        }
        if (back) {
            val staticCollections = if (style == BattleSession.SpriteStyle.MODERN_3D) {
                listOf("xy", "gen5")
            } else {
                listOf("gen5")
            }
            staticCollections.forEach { collection ->
                speciesNames.forEach { name -> candidates += staticBattleSprite(name, true, collection) }
            }
            candidates += placeholder(true)
        } else {
            candidates += dexSprite(species)
            candidates += "sprites/dex/${animationId(species)}.png"
        }
        return candidates.toList()
    }

    private fun trueBackSpritePaths(species: String): List<String> = when (animationId(species)) {
        "ironvaliant" -> listOf(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png",
            "sprites/gen5-back/ironvaliant.png"
        )
        else -> emptyList()
    }

    private fun battleSprite(species: String, back: Boolean, collection: String) =
        "sprites/${if (back) "$collection-back" else collection}/${animationId(species)}.gif"

    private fun staticBattleSprite(species: String, back: Boolean, collection: String) =
        "sprites/${if (back) "$collection-back" else collection}/${animationId(species)}.png"

    fun dexSprite(species: String) = "sprites/dex/${dexId(species)}.png"

    fun placeholder(back: Boolean) = "sprites/${if (back) "ani-back" else "ani"}/substitute.gif"

    fun trainer(trainer: String) = "sprites/trainers/${animationId(trainer)}.png"

    fun animationId(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun dexId(value: String): String {
        val original = value.trim().lowercase(Locale.ROOT)
        val normalized = original
            .replace("é", "e")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return when {
            original.contains('♀') || normalized == "nidoran-f" -> "nidoran-f"
            original.contains('♂') || normalized == "nidoran-m" -> "nidoran-m"
            else -> normalized
        }
    }
}
