package dev.adrian.showdown

import java.util.Locale

data class ShowdownSpriteResolutionPlan(
    val preferredRemoteCandidates: List<String>,
    val communityRemoteCandidates: List<String>,
    val regularRemoteCandidates: List<String>,
    val fallbackCandidates: List<String>,
    val usesModernAnimatedFallback: Boolean
) {
    val allCandidates: List<String>
        get() {
            if (!usesModernAnimatedFallback) {
                return preferredRemoteCandidates + regularRemoteCandidates + communityRemoteCandidates + fallbackCandidates
            }
            val modernLocalCandidates = fallbackCandidates.filter {
                it.startsWith("sprites/xyani")
            }
            val remainingFallbackCandidates = fallbackCandidates - modernLocalCandidates.toSet()
            return preferredRemoteCandidates + regularRemoteCandidates + communityRemoteCandidates + modernLocalCandidates + remainingFallbackCandidates
        }
}

object ShowdownAssetPaths {
    private val itemSlugAliases = mapOf(
        "assaultvest" to "assault-vest",
        "abilityshield" to "ability-shield",
        "boosterenergy" to "booster-energy",
        "choicescarf" to "choice-scarf",
        "choicespecs" to "choice-specs",
        "choiceband" to "choice-band",
        "clearamulet" to "clear-amulet",
        "covertcloak" to "covert-cloak",
        "focussash" to "focus-sash",
        "heavydutyboots" to "heavy-duty-boots",
        "lifeorb" to "life-orb",
        "protectivepads" to "protective-pads",
        "rockyhelmet" to "rocky-helmet",
        "safetygoggles" to "safety-goggles",
        "weaknesspolicy" to "weakness-policy"
    )

    private val hdNumberedSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/",
        "https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/"
    )

    private val regularSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados/",
        "https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados/",
        "https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados/",
        "https://www.pkparaiso.com/imagenes/xy/sprites/animados/"
    )

    private val shinyFrontSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-shiny/",
        "https://www.pkparaiso.com/imagenes/xy/sprites/animados-shiny/",
        "https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados-shiny/"
    )

    private val hdBackSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/"
    )

    private val regularBackSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados/",
        "https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados-espalda/",
        "https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-espalda/",
        "https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda/"
    )

    private val shinyBackSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-espalda-shiny/",
        "https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda-shiny/"
    )

    private val communityAnimatedSpriteRoots = mapOf(
        false to mapOf(
            BattleSpriteSide.OPPONENT to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/FRONT/",
            BattleSpriteSide.PLAYER to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/BACK/"
        ),
        true to mapOf(
            BattleSpriteSide.OPPONENT to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/FRONT_SHINY/",
            BattleSpriteSide.PLAYER to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/BACK_SHINY/"
        )
    )

    fun battleSprite(request: BattleSpriteRequest): String {
        return animatedBattleSprite(request.species, request.side, request.style.animatedCollection, request.shiny)
    }

    fun battleSpriteResolutionPlan(request: BattleSpriteRequest): ShowdownSpriteResolutionPlan {
        return createResolutionPlan(
            candidates = buildBattleSpriteCandidates(request),
            usesModernAnimatedFallback = request.style == BattleSession.SpriteStyle.MODERN_3D
        )
    }

    fun battleSpriteCandidates(request: BattleSpriteRequest): List<String> =
        battleSpriteResolutionPlan(request).allCandidates

    fun dexSpriteResolutionPlan(species: String, shiny: Boolean = false): ShowdownSpriteResolutionPlan {
        return createResolutionPlan(
            candidates = buildDexSpriteCandidates(species, shiny),
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
        val collections = listOf(request.style.animatedCollection)
        if (request.style == BattleSession.SpriteStyle.MODERN_3D) {
            if (request.backFacing) {
                hdBackSpriteCandidates(speciesNames, request.shiny).forEach { candidates += it }
                communityAnimatedSpriteCandidates(speciesNames, BattleSpriteSide.PLAYER, request.shiny).forEach { candidates += it }
                regularBackSpriteCandidates(speciesNames, request.shiny).forEach { candidates += it }
            } else {
                hdFrontSpriteCandidates(speciesNames, request.shiny).forEach { candidates += it }
                communityAnimatedSpriteCandidates(speciesNames, BattleSpriteSide.OPPONENT, request.shiny).forEach { candidates += it }
                regularFrontSpriteCandidates(speciesNames, request.shiny).forEach { candidates += it }
            }
        }
        collections.forEach { collection ->
            speciesNames.forEach { name -> candidates += animatedBattleSprite(name, request.side, collection, request.shiny) }
        }
        return candidates.toList()
    }

    fun dexSpriteCandidates(species: String, shiny: Boolean = false): List<String> = dexSpriteResolutionPlan(species, shiny).allCandidates

    private fun buildDexSpriteCandidates(species: String, shiny: Boolean): List<String> {
        val speciesNames = spriteSpeciesNames(species)
        return linkedSetOf<String>().apply {
            hdFrontSpriteCandidates(speciesNames, shiny).forEach { add(it) }
            communityAnimatedSpriteCandidates(speciesNames, BattleSpriteSide.OPPONENT, shiny).forEach { add(it) }
            staticDexSpriteCandidates(species, shiny).forEach(::add)
        }.toList()
    }

    private fun createResolutionPlan(
        candidates: List<String>,
        usesModernAnimatedFallback: Boolean
    ): ShowdownSpriteResolutionPlan {
        val firstLocalCandidate = candidates.indexOfFirst { it.startsWith("sprites/") }
            .takeIf { it >= 0 }
            ?: candidates.size
        val remoteCandidates = candidates.take(firstLocalCandidate)
        return ShowdownSpriteResolutionPlan(
            preferredRemoteCandidates = remoteCandidates
                .filterNot(::isCommunityAnimatedCandidate)
                .filterNot(::isRegularAnimatedCandidate),
            communityRemoteCandidates = remoteCandidates.filter(::isCommunityAnimatedCandidate),
            regularRemoteCandidates = remoteCandidates.filter(::isRegularAnimatedCandidate),
            fallbackCandidates = candidates.drop(firstLocalCandidate),
            usesModernAnimatedFallback = usesModernAnimatedFallback
        )
    }

    private fun isCommunityAnimatedCandidate(path: String) =
        path.startsWith("https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/")

    private fun isRegularAnimatedCandidate(path: String) =
        path.contains("/sprites/animados/") ||
            path.contains("/sprites/animados-shiny/") ||
            path.contains("/sprites/animados-espalda/") ||
            path.contains("/sprites/animados-espalda-shiny/") ||
            path.contains("/sprites/animados-sinbordes/")

    fun pokeApiLookupNames(species: String): List<String> = spriteSpeciesNames(species).map { pokeApiSlug(it) }

    fun spriteSpeciesNamesForExternalLookup(species: String): List<String> = spriteSpeciesNames(species)

    fun backSpriteIndexUrls(shiny: Boolean = false): List<String> {
        val normalIndexes = listOf(
            "https://www.pkparaiso.com/espada_escudo/sprites_pokemon_espalda.php",
            "https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon_espalda.php",
            "https://www.pkparaiso.com/sol-luna/sprites_pokemon_espalda.php",
            "https://www.pkparaiso.com/xy/sprites_pokemon_espalda.php"
        )
        if (!shiny) return normalIndexes
        return listOf(
            normalIndexes[0],
            "https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon_variocolores_espalda.php",
            "https://www.pkparaiso.com/xy/sprites_pokemon_variocolores_espalda.php",
            normalIndexes[1],
            normalIndexes[2],
            normalIndexes[3]
        )
    }

    fun highResolutionBackSpriteIndexUrls(shiny: Boolean = false): List<String> =
        backSpriteIndexUrls(shiny)

    fun frontSpriteIndexUrls(shiny: Boolean = false): List<String> {
        val normalIndexes = listOf(
            "https://www.pkparaiso.com/espada_escudo/sprites_pokemon.php",
            "https://www.pkparaiso.com/ultra-sol-ultra-luna/sprites_pokemon_sin_bordes.php",
            "https://www.pkparaiso.com/sol-luna/sprites_pokemon.php",
            "https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon.php",
            "https://www.pkparaiso.com/xy/sprites_pokemon.php"
        )
        if (!shiny) return normalIndexes
        return listOf(
            normalIndexes[0],
            normalIndexes[1],
            "https://www.pkparaiso.com/sol-luna/sprites_pokemon_variocolores.php",
            "https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon_variocolores.php",
            "https://www.pkparaiso.com/xy/sprites_pokemon_variocolores.php"
        )
    }

    fun highResolutionFrontSpriteIndexUrls(shiny: Boolean = false): List<String> =
        frontSpriteIndexUrls(shiny)

    fun pokeApiAnimatedSprite(number: Int, side: BattleSpriteSide, shiny: Boolean = false): String {
        val facingPath = if (side == BattleSpriteSide.PLAYER) "back/" else ""
        val shinyPath = if (shiny) "shiny/" else ""
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/${facingPath}${shinyPath}$number.gif"
    }

    private fun battleSprite(species: String, side: BattleSpriteSide, collection: String, shiny: Boolean = false) =
        animatedBattleSprite(species, side, collection, shiny)

    private fun animatedBattleSprite(species: String, side: BattleSpriteSide, collection: String, shiny: Boolean = false) =
        "sprites/${spriteCollection(collection, side, shiny)}/${animationId(species)}.gif"

    private fun spriteCollection(collection: String, side: BattleSpriteSide, shiny: Boolean): String {
        val facingCollection = if (side == BattleSpriteSide.PLAYER) "$collection-back" else collection
        return if (shiny) "$facingCollection-shiny" else facingCollection
    }

    private fun hdFrontSpriteCandidates(speciesNames: List<String>, shiny: Boolean): List<String> =
        speciesNames.flatMap { species ->
            hdSpriteNames(species).flatMap { spriteName ->
                hdNumberedSpriteRoots.map { root -> "$root${spriteFileName(spriteName, backFacing = false, shiny)}" }
            }
        }

    private fun regularFrontSpriteCandidates(speciesNames: List<String>, shiny: Boolean): List<String> =
        speciesNames.flatMap { species ->
            hdSpriteNames(species).flatMap { spriteName ->
                buildList {
                    if (shiny) shinyFrontSpriteRoots.forEach { root -> add("$root${spriteFileName(spriteName, backFacing = false, shiny = false)}") }
                    regularSpriteRoots.forEach { root -> add("$root${spriteFileName(spriteName, backFacing = false, shiny)}") }
                }
            }
        }

    private fun hdBackSpriteCandidates(speciesNames: List<String>, shiny: Boolean): List<String> =
        speciesNames.flatMap { species ->
            hdSpriteNames(species).flatMap { spriteName ->
                hdBackSpriteRoots.map { root ->
                    "$root${spriteFileName(spriteName, backFacing = true, shiny)}"
                }
            }
        }

    private fun regularBackSpriteCandidates(speciesNames: List<String>, shiny: Boolean): List<String> =
        speciesNames.flatMap { species ->
            hdSpriteNames(species).flatMap { spriteName ->
                buildList {
                    regularBackSpriteRoots.forEach { root ->
                        add(
                            if (root.endsWith("animados-espalda/")) "$root${spriteFileName(spriteName, backFacing = false, shiny)}"
                            else "$root${spriteFileName(spriteName, backFacing = true, shiny)}"
                        )
                    }
                    if (shiny) shinyBackSpriteRoots.forEach { root -> add("$root${spriteFileName(spriteName, backFacing = false, shiny = false)}") }
                }
            }
        }

    private fun communityAnimatedSpriteCandidates(
        speciesNames: List<String>,
        side: BattleSpriteSide,
        shiny: Boolean
    ): List<String> = speciesNames.flatMap { species ->
        val names = communityAnimatedSpriteNames(species)
        val fileNames = buildList {
            names.forEach { name ->
                add(name)
                if (shiny) {
                    add("$name shiny")
                }
            }
            if (side == BattleSpriteSide.PLAYER) {
                names.forEach { name ->
                    add("$name back")
                    add("${name}_back")
                    if (shiny) {
                        add("$name back shiny")
                        add("${name}_back_shiny")
                        add("$name shiny back")
                    }
                }
            }
        }
        fileNames.map { fileName ->
            val encodedName = fileName.replace(" ", "%20")
            communityAnimatedSpriteRoots[shiny]?.get(side)?.let { root -> "$root$encodedName.gif" }
        }.filterNotNull()
    }.distinct()

    private fun spriteFileName(spriteName: String, backFacing: Boolean, shiny: Boolean): String =
        buildString {
            append(spriteName)
            if (backFacing) append("-back")
            if (shiny) append("-s")
            append(".gif")
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
        val original = species.trim()
        val normalized = normalizeSpriteName(species)
        return linkedSetOf(
            original,
            original.replace(' ', '_'),
            original.replace(Regex("[\\s-]+"), ""),
            normalized.uppercase(Locale.ROOT),
            normalized.replace('-', '_').uppercase(Locale.ROOT),
            animationId(species).uppercase(Locale.ROOT)
        ).toList()
    }

    fun dexSprite(species: String, shiny: Boolean = false): String {
        val collection = if (shiny) "dex-shiny" else "dex"
        return "sprites/$collection/${dexId(species)}.png"
    }

    fun staticDexSpriteCandidates(species: String, shiny: Boolean = false): List<String> = spriteSpeciesNames(species)
        .flatMap { name ->
            listOf(
                dexSprite(name, shiny),
                "sprites/${if (shiny) "dex-shiny" else "dex"}/${animationId(name)}.png"
            )
        }
        .distinct()

    fun staticBackSpriteCandidates(species: String): List<String> = listOf(
        "sprites/gen5-back/${animationId(species)}.png"
    )

    fun trainer(trainer: String) = "sprites/trainers/${animationId(trainer)}.png"

    fun itemSprite(item: String): String? {
        return normalizedItemId(item)?.let { "sprites/itemicons/$it.png" }
    }

    fun itemSpriteCandidates(item: String): List<String> {
        val id = normalizedItemId(item) ?: return emptyList()
        return linkedSetOf(id, itemSlugAliases[id])
            .filterNotNull()
            .flatMap { slug ->
                listOf(
                    "sprites/itemicons/$slug.png",
                    "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/$slug.png"
                )
            }
            .distinct()
    }

    private fun normalizedItemId(item: String): String? {
        val id = item.trim()
            .lowercase(Locale.ROOT)
            .replace("é", "e")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return id.takeUnless {
            it.isBlank() || it == "noitem" || it == "no-item" || it == "unknownitem" || it == "unknown-item"
        }
    }

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
