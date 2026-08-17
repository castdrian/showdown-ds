package dev.adrian.showdown

import java.util.Locale

object ShowdownAssetPaths {
    fun battleSprite(request: BattleSpriteRequest): String {
        return animatedBattleSprite(request.species, request.side, request.style.animatedCollection)
    }

    fun battleSpriteCandidates(request: BattleSpriteRequest): List<String> {
        val candidates = linkedSetOf<String>()
        val baseSpecies = request.species.substringBefore('-').trim()
        val speciesNames = buildList {
            add(request.species)
            if (baseSpecies.isNotEmpty() && !baseSpecies.equals(request.species.trim(), ignoreCase = true)) add(baseSpecies)
        }
        val collections = buildList {
            add(request.style.animatedCollection)
            if (request.style != BattleSession.SpriteStyle.CLASSIC_2D) add(BattleSession.SpriteStyle.CLASSIC_2D.animatedCollection)
        }
        val verifiedBackPaths = if (request.backFacing) trueBackSpritePaths(request.species) else emptyList()
        verifiedBackPaths.forEach { candidates += it }
        val hasVerifiedBackSprite = verifiedBackPaths.isNotEmpty()
        val staticCollections = request.style.staticCollections
        if (!hasVerifiedBackSprite) {
            collections.forEach { collection ->
                speciesNames.forEach { name -> candidates += battleSprite(name, request.side, collection) }
            }
        }
        if (request.backFacing) {
            if (!hasVerifiedBackSprite) {
                staticCollections.forEach { collection ->
                    speciesNames.forEach { name -> candidates += staticBattleSprite(name, BattleSpriteSide.PLAYER, collection) }
                }
            }
            candidates += placeholder(BattleSpriteSide.PLAYER)
        } else {
            staticCollections.forEach { collection ->
                speciesNames.forEach { name -> candidates += staticBattleSprite(name, BattleSpriteSide.OPPONENT, collection) }
            }
            candidates += dexSprite(request.species)
            candidates += "sprites/dex/${animationId(request.species)}.png"
        }
        return candidates.toList()
    }

    private fun trueBackSpritePaths(species: String): List<String> = when (animationId(species)) {
        "ironvaliant" -> listOf(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"
        )
        else -> emptyList()
    }

    private fun battleSprite(species: String, side: BattleSpriteSide, collection: String) =
        animatedBattleSprite(species, side, collection)

    private fun animatedBattleSprite(species: String, side: BattleSpriteSide, collection: String) =
        "sprites/${if (side == BattleSpriteSide.PLAYER) "$collection-back" else collection}/${animationId(species)}.gif"

    private fun staticBattleSprite(species: String, side: BattleSpriteSide, collection: String) =
        "sprites/${if (side == BattleSpriteSide.PLAYER) "$collection-back" else collection}/${animationId(species)}.png"

    fun dexSprite(species: String) = "sprites/dex/${dexId(species)}.png"

    fun placeholder(side: BattleSpriteSide) = "sprites/${if (side == BattleSpriteSide.PLAYER) "ani-back" else "ani"}/substitute.gif"

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
