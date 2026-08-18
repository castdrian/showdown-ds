package dev.adrian.showdown

import org.json.JSONObject
import java.util.Locale

data class ShowdownSpriteResolutionPlan(
    val preferredRemoteCandidates: List<String>,
    val communityRemoteCandidates: List<String>,
    val verifiedRemoteCandidates: List<String>,
    val regularRemoteCandidates: List<String>,
    val fallbackCandidates: List<String>,
    val usesModernAnimatedFallback: Boolean
) {
    val allCandidates: List<String>
        get() {
            if (!usesModernAnimatedFallback) {
                return preferredRemoteCandidates + communityRemoteCandidates + verifiedRemoteCandidates + regularRemoteCandidates + fallbackCandidates
            }
            val modernLocalCandidates = fallbackCandidates.filter {
                it.startsWith("sprites/xyani") || it.startsWith("sprites/xy/")
            }
            val remainingFallbackCandidates = fallbackCandidates - modernLocalCandidates.toSet()
            return preferredRemoteCandidates + communityRemoteCandidates + regularRemoteCandidates + modernLocalCandidates + verifiedRemoteCandidates + remainingFallbackCandidates
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

    private val hdBackSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/"
    )

    private val regularBackSpriteRoots = listOf(
        "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados/",
        "https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados-espalda/",
        "https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-espalda/",
        "https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda/"
    )

    private val communityAnimatedSpriteRoots = mapOf(
        BattleSpriteSide.OPPONENT to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/FRONT/",
        BattleSpriteSide.PLAYER to "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/BACK/"
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
        val verifiedBackPaths = if (request.backFacing) trueBackSpritePaths(request.species) else emptyList()
        verifiedBackPaths.forEach { candidates += it }
        val staticCollections = request.style.staticCollections
        collections.forEach { collection ->
            speciesNames.forEach { name -> candidates += animatedBattleSprite(name, request.side, collection, request.shiny) }
        }
        if (request.backFacing) {
            staticCollections.forEach { collection ->
                speciesNames.forEach { name -> candidates += staticBattleSprite(name, BattleSpriteSide.PLAYER, collection, request.shiny) }
            }
            candidates += placeholder(BattleSpriteSide.PLAYER)
        } else {
            staticCollections.forEach { collection ->
                speciesNames.forEach { name -> candidates += staticBattleSprite(name, BattleSpriteSide.OPPONENT, collection, request.shiny) }
            }
            candidates += dexSprite(request.species)
            candidates += "sprites/dex/${animationId(request.species)}.png"
            candidates += placeholder(BattleSpriteSide.OPPONENT)
        }
        return candidates.toList()
    }

    fun dexSpriteCandidates(species: String): List<String> = dexSpriteResolutionPlan(species).allCandidates

    private fun buildDexSpriteCandidates(species: String): List<String> {
        val speciesNames = spriteSpeciesNames(species)
        return linkedSetOf<String>().apply {
            hdFrontSpriteCandidates(speciesNames, false).forEach { add(it) }
            communityAnimatedSpriteCandidates(speciesNames, BattleSpriteSide.OPPONENT, false).forEach { add(it) }
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
        val remoteCandidates = candidates.take(firstLocalCandidate)
        return ShowdownSpriteResolutionPlan(
            preferredRemoteCandidates = remoteCandidates
                .filterNot(::isCommunityAnimatedCandidate)
                .filterNot(::isVerifiedRemoteCandidate)
                .filterNot(::isRegularAnimatedCandidate),
            communityRemoteCandidates = remoteCandidates.filter(::isCommunityAnimatedCandidate),
            verifiedRemoteCandidates = remoteCandidates.filter(::isVerifiedRemoteCandidate),
            regularRemoteCandidates = remoteCandidates.filter(::isRegularAnimatedCandidate),
            fallbackCandidates = candidates.drop(firstLocalCandidate),
            usesModernAnimatedFallback = usesModernAnimatedFallback
        )
    }

    private fun isCommunityAnimatedCandidate(path: String) =
        path.startsWith("https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/")

    private fun isVerifiedRemoteCandidate(path: String) =
        path == "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"

    private fun isRegularAnimatedCandidate(path: String) =
        path.contains("/sprites/animados/") || path.contains("/sprites/animados-espalda/")

    fun pokeApiLookupNames(species: String): List<String> = spriteSpeciesNames(species).map { pokeApiSlug(it) }

    fun backSpriteIndexUrls() = listOf(
        "https://www.pkparaiso.com/espada_escudo/sprites_pokemon_espalda.php",
        "https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon_espalda.php",
        "https://www.pkparaiso.com/sol-luna/sprites_pokemon_espalda.php",
        "https://www.pkparaiso.com/xy/sprites_pokemon_espalda.php"
    )

    fun pokeApiNationalDexNumber(payload: String): Int? {
        return runCatching {
            JSONObject(payload)
                .optJSONObject("species")
                ?.optString("url")
                ?.trimEnd('/')
                ?.substringAfterLast('/')
                ?.toIntOrNull()
        }.getOrNull()?.takeIf { it > 0 }
    }

    fun pokeApiAnimatedSprite(number: Int, side: BattleSpriteSide, shiny: Boolean = false): String {
        val facingPath = if (side == BattleSpriteSide.PLAYER) "back/" else ""
        val shinyPath = if (shiny) "shiny/" else ""
        return "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/${facingPath}${shinyPath}$number.gif"
    }

    fun pokeApiHighResolutionSprite(number: Int, shiny: Boolean = false): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/${if (shiny) "shiny/" else ""}$number.png"

    fun pokeApiStandardSprite(number: Int, side: BattleSpriteSide, shiny: Boolean = false): String =
        "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/${if (side == BattleSpriteSide.PLAYER) "back/" else ""}${if (shiny) "shiny/" else ""}$number.png"

    fun hdAnimatedSpriteCandidates(number: Int, side: BattleSpriteSide, shiny: Boolean = false): List<String> =
        (if (side == BattleSpriteSide.PLAYER) hdBackSpriteRoots else hdNumberedSpriteRoots).map { root ->
            if (side == BattleSpriteSide.PLAYER && root.endsWith("animados-espalda/")) {
                "$root$number${if (shiny) "-s" else ""}.gif"
            } else {
                "$root$number${if (side == BattleSpriteSide.PLAYER) "-back" else ""}${if (shiny) "-s" else ""}.gif"
            }
        }

    private fun trueBackSpritePaths(species: String): List<String> = when (animationId(species)) {
        "ironvaliant" -> listOf(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"
        )
        else -> emptyList()
    }

    private fun battleSprite(species: String, side: BattleSpriteSide, collection: String, shiny: Boolean = false) =
        animatedBattleSprite(species, side, collection, shiny)

    private fun animatedBattleSprite(species: String, side: BattleSpriteSide, collection: String, shiny: Boolean = false) =
        "sprites/${spriteCollection(collection, side, shiny)}/${animationId(species)}.gif"

    private fun staticBattleSprite(species: String, side: BattleSpriteSide, collection: String, shiny: Boolean = false) =
        "sprites/${spriteCollection(collection, side, shiny)}/${animationId(species)}.png"

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
                regularSpriteRoots.map { root -> "$root${spriteFileName(spriteName, backFacing = false, shiny)}" }
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
                regularBackSpriteRoots.map { root ->
                    if (root.endsWith("animados-espalda/")) "$root${spriteFileName(spriteName, backFacing = false, shiny)}"
                    else "$root${spriteFileName(spriteName, backFacing = true, shiny)}"
                }
            }
        }

    private fun communityAnimatedSpriteCandidates(
        speciesNames: List<String>,
        side: BattleSpriteSide,
        shiny: Boolean
    ): List<String> = if (shiny) emptyList() else speciesNames.flatMap { species ->
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

    fun dexSprite(species: String) = "sprites/dex/${dexId(species)}.png"

    fun placeholder(side: BattleSpriteSide) = "sprites/${if (side == BattleSpriteSide.PLAYER) "ani-back" else "ani"}/substitute.gif"

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
