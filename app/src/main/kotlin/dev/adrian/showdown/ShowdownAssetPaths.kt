package dev.adrian.showdown

import java.util.Locale

data class ShowdownSpriteResolutionPlan(
    val preferredRemoteCandidates: List<String>,
    val fallbackCandidates: List<String>,
    val usesModernAnimatedFallback: Boolean
) {
    val allCandidates: List<String>
        get() = preferredRemoteCandidates + fallbackCandidates
}

object ShowdownAssetPaths {
    private val hdSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/",
        "https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/"
    )

    private val communityAnimatedSpriteRoots = mapOf(
        BattleSpriteSide.OPPONENT to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/master/FRONT/",
        BattleSpriteSide.PLAYER to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/master/BACK/"
    )

    fun battleSprite(request: BattleSpriteRequest): String {
        return animatedBattleSprite(request.species, request.side, request.style.animatedCollection)
    }

    fun battleSpriteResolutionPlan(request: BattleSpriteRequest): ShowdownSpriteResolutionPlan {
        return createResolutionPlan(
            candidates = buildBattleSpriteCandidates(request),
            usesModernAnimatedFallback = request.style == BattleSession.SpriteStyle.MODERN_3D
        )
    }

    fun battleSpriteCandidates(request: BattleSpriteRequest): List<String> =
        battleSpriteResolutionPlan(request).allCandidates

    fun dexSpriteResolutionPlan(species: String): ShowdownSpriteResolutionPlan {
        return createResolutionPlan(
            candidates = buildDexSpriteCandidates(species),
            usesModernAnimatedFallback = true
        )
    }

    private fun buildBattleSpriteCandidates(request: BattleSpriteRequest): List<String> {
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
        if (request.style == BattleSession.SpriteStyle.MODERN_3D) {
            if (request.backFacing) {
                hdBackSpriteCandidates(speciesNames).forEach { candidates += it }
                communityAnimatedSpriteCandidates(speciesNames, BattleSpriteSide.PLAYER).forEach { candidates += it }
            } else {
                hdFrontSpriteCandidates(speciesNames).forEach { candidates += it }
                communityAnimatedSpriteCandidates(speciesNames, BattleSpriteSide.OPPONENT).forEach { candidates += it }
            }
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

    fun dexSpriteCandidates(species: String): List<String> = dexSpriteResolutionPlan(species).allCandidates

    private fun buildDexSpriteCandidates(species: String): List<String> {
        val speciesNames = spriteSpeciesNames(species)
        return linkedSetOf<String>().apply {
            hdFrontSpriteCandidates(speciesNames).forEach { add(it) }
            communityAnimatedSpriteCandidates(speciesNames, BattleSpriteSide.OPPONENT).forEach { add(it) }
            add(dexSprite(species))
            add("sprites/dex/${animationId(species)}.png")
        }.toList()
    }

    private fun createResolutionPlan(
        candidates: List<String>,
        usesModernAnimatedFallback: Boolean
    ): ShowdownSpriteResolutionPlan {
        val firstLocalCandidate = candidates.indexOfFirst { it.startsWith("sprites/") }
            .takeIf { it >= 0 }
            ?: candidates.size
        return ShowdownSpriteResolutionPlan(
            preferredRemoteCandidates = candidates.take(firstLocalCandidate),
            fallbackCandidates = candidates.drop(firstLocalCandidate),
            usesModernAnimatedFallback = usesModernAnimatedFallback
        )
    }

    fun pokeApiLookupNames(species: String): List<String> = spriteSpeciesNames(species).map { pokeApiSlug(it) }

    fun pokeApiAnimatedSprite(number: Int, side: BattleSpriteSide): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/${if (side == BattleSpriteSide.PLAYER) "back/" else ""}$number.gif"

    fun pokeApiHighResolutionSprite(number: Int): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/$number.png"

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

    private fun hdFrontSpriteCandidates(speciesNames: List<String>): List<String> =
        speciesNames.flatMap { species ->
            hdSpriteNames(species).flatMap { spriteName ->
                hdSpriteRoots.map { root -> "$root$spriteName.gif" }
            }
        }

    private fun hdBackSpriteCandidates(speciesNames: List<String>): List<String> =
        speciesNames.flatMap { species ->
            hdSpriteNames(species).flatMap { spriteName ->
                hdSpriteRoots.map { root -> "$root${spriteName}-back.gif" }
            }
        }

    private fun communityAnimatedSpriteCandidates(
        speciesNames: List<String>,
        side: BattleSpriteSide
    ): List<String> = speciesNames.flatMap { species ->
        communityAnimatedSpriteNames(species).flatMap { spriteName ->
            buildList {
                communityAnimatedSpriteRoots[side]?.let { root ->
                    add("$root$spriteName.gif")
                    if (side == BattleSpriteSide.PLAYER) {
                        add("$root${spriteName}_back.gif")
                        add("$root${spriteName}%20back.gif")
                    }
                }
            }
        }
    }

    private fun spriteSpeciesNames(species: String): List<String> {
        val baseSpecies = species.substringBefore('-').trim()
        return buildList {
            add(species)
            if (baseSpecies.isNotEmpty() && !baseSpecies.equals(species.trim(), ignoreCase = true)) add(baseSpecies)
        }
    }

    private fun pokeApiSlug(species: String) = normalizeSpriteName(species)

    private fun normalizeSpriteName(species: String) = species.trim()
        .lowercase(Locale.ROOT)
        .replace("é", "e")
        .replace("♀", "-f")
        .replace("♂", "-m")
        .replace(Regex("[^a-z0-9]+"), "-")
        .trim('-')

    private fun hdSpriteNames(species: String): List<String> {
        val normalized = normalizeSpriteName(species)
        return linkedSetOf(animationId(species), normalized, dexId(species)).toList()
    }

    private fun communityAnimatedSpriteNames(species: String): List<String> {
        val normalized = normalizeSpriteName(species)
        return linkedSetOf(
            normalized.uppercase(Locale.ROOT),
            normalized.replace('-', '_').uppercase(Locale.ROOT),
            animationId(species).uppercase(Locale.ROOT)
        ).toList()
    }

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
