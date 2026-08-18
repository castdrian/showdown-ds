package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownAssetPathsTest {
    @Test
    fun usesShowdownsDistinctDexAndAnimatedSpriteIdentifiers() {
        assertEquals("sprites/dex/rotom-wash.png", ShowdownAssetPaths.dexSprite("Rotom-Wash"))
        assertEquals("sprites/xyani/rotomwash.gif", ShowdownAssetPaths.battleSprite(BattleSpriteRequest.forOpponent("Rotom-Wash", BattleSession.SpriteStyle.MODERN_3D)))
        assertEquals("sprites/xyani-back/rotomwash.gif", ShowdownAssetPaths.battleSprite(BattleSpriteRequest.forPlayer("Rotom-Wash", BattleSession.SpriteStyle.MODERN_3D)))
    }

    @Test
    fun suppliesPokeApiItemFallbacksWhenShowdownHasNoIcon() {
        assertEquals(
            listOf(
                "sprites/itemicons/assault-vest.png",
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/assault-vest.png"
            ),
            ShowdownAssetPaths.itemSpriteCandidates("Assault Vest")
        )
        assertTrue(
            ShowdownAssetPaths.itemSpriteCandidates("assaultvest").contains(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/items/assault-vest.png"
            )
        )
    }

    @Test
    fun keepsHyphenatedDexFormsAndNormalizesAnimatedSprites() {
        assertEquals("sprites/dex/ho-oh.png", ShowdownAssetPaths.dexSprite("Ho-Oh"))
        assertEquals("sprites/dex/nidoran-m.png", ShowdownAssetPaths.dexSprite("Nidoran♂"))
        assertEquals("sprites/xyani/hooh.gif", ShowdownAssetPaths.battleSprite(BattleSpriteRequest.forOpponent("Ho-Oh", BattleSession.SpriteStyle.MODERN_3D)))
    }

    @Test
    fun fallsBackToTheBaseSpeciesForUnavailableFormSprites() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forOpponent("Furfrou-La Reine", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertEquals(
            listOf(
                "sprites/xyani/furfroulareine.gif",
                "sprites/xyani/furfrou.gif",
                "sprites/xy/furfroulareine.png",
                "sprites/xy/furfrou.png",
                "sprites/dex/furfrou-la-reine.png",
                "sprites/dex/furfroulareine.png"
            ),
            candidates.filter { it.startsWith("sprites/") }
        )
        assertTrue(candidates.filter { it.startsWith("https://") }.isNotEmpty())
    }

    @Test
    fun neverUsesSubstituteAsASpeciesFallback() {
        val requests = listOf(
            BattleSpriteRequest.forOpponent("Furfrou-La Reine", BattleSession.SpriteStyle.MODERN_3D),
            BattleSpriteRequest.forPlayer("Furfrou-La Reine", BattleSession.SpriteStyle.MODERN_3D)
        )

        requests.forEach { request ->
            assertTrue(ShowdownAssetPaths.battleSpriteCandidates(request).none { it.contains("substitute") })
        }
    }

    @Test
    fun fallsBackToRegularAnimationAndDexSpritesForNewSpecies() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forOpponent("Iron Hands", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertEquals(
            listOf(
                "sprites/xyani/ironhands.gif",
                "sprites/xy/ironhands.png",
                "sprites/dex/iron-hands.png",
                "sprites/dex/ironhands.png"
            ),
            candidates.filter { it.startsWith("sprites/") }
        )
        assertTrue(candidates.filter { it.startsWith("https://") }.isNotEmpty())
    }

    @Test
    fun triesHdFrontArtworkBeforeRegularShowdownArtwork() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forOpponent("Rotom-Wash", BattleSession.SpriteStyle.MODERN_3D)
        )
        val hdCandidate = candidates.first { it.endsWith("/rotom-wash.gif") }
        assertTrue(hdCandidate.startsWith("https://www.pkparaiso.com/"))
        assertTrue(candidates.indexOf(hdCandidate) < candidates.indexOf("sprites/xyani/rotomwash.gif"))
    }

    @Test
    fun keepsBanetteAnimationAheadOfStaticFrontFallbacks() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forOpponent("Banette", BattleSession.SpriteStyle.MODERN_3D)
        )
        val animatedCandidate = "https://www.pkparaiso.com/imagenes/xy/sprites/animados/banette.gif"

        assertTrue(plan.regularRemoteCandidates.contains(animatedCandidate))
        assertTrue(plan.allCandidates.indexOf(animatedCandidate) < plan.allCandidates.indexOf("sprites/xyani/banette.gif"))
    }

    @Test
    fun keepsRegularPkParaisoArtworkOutOfTheHdResolutionTier() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forOpponent("Rotom-Wash", BattleSession.SpriteStyle.MODERN_3D)
        )

        assertTrue(plan.preferredRemoteCandidates.all { !it.contains("/sprites/animados/") })
        assertTrue(plan.regularRemoteCandidates.any { it.contains("/sprites/animados/") })
        assertTrue(plan.fallbackCandidates.first().contains("sprites/xyani/rotomwash.gif"))
    }

    @Test
    fun dexSpriteRequestsAlsoTryHdFrontArtworkFirst() {
        val candidates = ShowdownAssetPaths.dexSpriteCandidates("Rotom-Wash")
        val hdCandidate = candidates.first { it.endsWith("/rotom-wash.gif") }
        assertTrue(hdCandidate.startsWith("https://www.pkparaiso.com/"))
        assertTrue(candidates.indexOf(hdCandidate) < candidates.indexOf("sprites/dex/rotom-wash.png"))
    }

    @Test
    fun probesCreditPermittedAnimatedCommunityArtworkBeforePixelFallbacks() {
        val frontCandidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forOpponent("Skeledirge", BattleSession.SpriteStyle.MODERN_3D)
        )
        val backCandidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Sneasler", BattleSession.SpriteStyle.MODERN_3D)
        )
        val frontCandidate = "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/FRONT/SKELEDIRGE.gif"
        val backCandidate = "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/BACK/SNEASLER.gif"
        assertTrue(frontCandidates.contains(frontCandidate))
        assertTrue(backCandidates.contains(backCandidate))
        assertTrue(frontCandidates.indexOf(frontCandidate) < frontCandidates.indexOf("sprites/xyani/skeledirge.gif"))
        assertTrue(backCandidates.indexOf(backCandidate) < backCandidates.indexOf("sprites/xyani-back/sneasler.gif"))
    }

    @Test
    fun keepsCommunityAnimationInModernRemoteTier() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forOpponent("Skeledirge", BattleSession.SpriteStyle.MODERN_3D)
        )
        val communityCandidate = "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/FRONT/SKELEDIRGE.gif"

        assertFalse(plan.preferredRemoteCandidates.contains(communityCandidate))
        assertTrue(plan.communityRemoteCandidates.contains(communityCandidate))
        assertTrue(plan.fallbackCandidates.first().contains("sprites/xyani/skeledirge.gif"))
    }

    @Test
    fun preservesCommunitySpriteCapitalizationVariants() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forOpponent("Gossifleur", BattleSession.SpriteStyle.MODERN_3D)
        )

        assertTrue(candidates.contains("https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/FRONT/Gossifleur.gif"))
    }

    @Test
    fun extractsNationalDexNumberFromAlternatePokeApiForms() {
        assertEquals(
            892,
            ShowdownAssetPaths.pokeApiNationalDexNumber(
                """{"id":10191,"species":{"url":"https://pokeapi.co/api/v2/pokemon-species/892/"}}"""
            )
        )
        assertEquals(null, ShowdownAssetPaths.pokeApiNationalDexNumber("{}"))
    }

    @Test
    fun defersVerifiedStaticBackArtworkUntilAnimatedFallbacksHaveBeenChecked() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
        )
        val verifiedBack = "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"

        assertTrue(plan.preferredRemoteCandidates.none { it == verifiedBack })
        assertEquals(listOf(verifiedBack), plan.verifiedRemoteCandidates)
        assertTrue(plan.fallbackCandidates.first().contains("sprites/xyani-back/ironvaliant.gif"))
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertTrue(candidates.indexOf(verifiedBack) > candidates.indexOf("sprites/xyani-back/ironvaliant.gif"))
    }

    @Test
    fun modernResolutionPlanDefersLocalLegacySpritesUntilAfterAnimatedFallback() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forOpponent("Iron Hands", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertTrue(plan.usesModernAnimatedFallback)
        assertTrue(plan.preferredRemoteCandidates.isNotEmpty())
        assertTrue(plan.preferredRemoteCandidates.all { it.startsWith("https://") })
        assertTrue(plan.fallbackCandidates.first().contains("sprites/xyani/ironhands.gif"))
        assertFalse(plan.fallbackCandidates.any { it.contains("gen5") })
    }

    @Test
    fun everyBattleResolutionPlanRejectsLegacyGen5Artwork() {
        val requests = listOf(
            BattleSpriteRequest.forOpponent("Alcremie", BattleSession.SpriteStyle.MODERN_3D),
            BattleSpriteRequest.forPlayer("Alcremie", BattleSession.SpriteStyle.MODERN_3D)
        )

        requests.forEach { request ->
            val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(request)
            assertTrue(plan.usesModernAnimatedFallback)
            assertTrue(plan.allCandidates.none { it.contains("gen5", ignoreCase = true) })
        }
    }

    @Test
    fun fallsBackToStaticXyFrontSpritesForSpeciesWithoutAnimatedAssets() {
        assertTrue(
            ShowdownAssetPaths.battleSpriteCandidates(
                BattleSpriteRequest.forOpponent("Pecharunt", BattleSession.SpriteStyle.MODERN_3D)
            ).contains("sprites/xy/pecharunt.png")
        )
    }

    @Test
    fun neverFallsBackFromAPlayerBackSpriteToAFrontSprite() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertTrue(candidates.none { it.contains("/FRONT/") })
        assertTrue(candidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertEquals("sprites/xy-back/ironvaliant.png", candidates.last())
    }

    @Test
    fun keepsIronValiantFrontAndBackCandidatesSeparate() {
        val frontCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forOpponent("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))
        val backCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))

        assertTrue(frontCandidates.contains("sprites/xyani/ironvaliant.gif"))
        assertFalse(frontCandidates.any { it.contains("gen5") })
        assertFalse(frontCandidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertTrue(backCandidates.none { it.contains("/FRONT/") })
        assertTrue(backCandidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertEquals("sprites/xy-back/ironvaliant.png", backCandidates.last())
    }

    @Test
    fun probesHdBackArtworkBeforeRegularBackArtwork() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Alcremie", BattleSession.SpriteStyle.MODERN_3D)
        )
        val hdCandidate = candidates.first { it.endsWith("/alcremie-back.gif") }
        assertTrue(hdCandidate.startsWith("https://www.pkparaiso.com/"))
        assertTrue(candidates.indexOf(hdCandidate) < candidates.indexOf("sprites/xyani-back/alcremie.gif"))
    }

    @Test
    fun prefersHigherResolutionBackAnimationsBeforeLocalFallbacks() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Deoxys", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertTrue(candidates.contains("https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda/deoxys.gif"))
        assertTrue(candidates.indexOf("https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda/deoxys.gif") < candidates.indexOf("sprites/xyani-back/deoxys.gif"))
    }

    @Test
    fun probesSunMoonBackAnimationsForAlolanSpecies() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Incineroar", BattleSession.SpriteStyle.MODERN_3D)
        )
        val hdCandidate = "https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados-espalda/incineroar.gif"
        assertTrue(candidates.contains(hdCandidate))
        assertTrue(candidates.indexOf(hdCandidate) < candidates.indexOf("sprites/xyani-back/incineroar.gif"))
    }

    @Test
    fun keepsStandardSwordShieldAnimationsBehindGiantArtwork() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Corviknight", BattleSession.SpriteStyle.MODERN_3D)
        )
        val giantCandidate = "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back.gif"
        val standardCandidate = "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados/corviknight-back.gif"
        assertTrue(candidates.indexOf(giantCandidate) < candidates.indexOf(standardCandidate))
        assertTrue(candidates.indexOf(standardCandidate) < candidates.indexOf("sprites/xyani-back/corviknight.gif"))
    }

    @Test
    fun keepsOfficialBackSourcesInTheRegularRemoteTier() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forPlayer("Incineroar", BattleSession.SpriteStyle.MODERN_3D)
        )
        val officialBack = "https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados-espalda/incineroar.gif"
        val communityBack = "https://raw.githubusercontent.com/Ghasty001/Animated_sprites_by_Ghasty001/main/BACK/INCINEROAR.gif"

        assertFalse(plan.preferredRemoteCandidates.contains(officialBack))
        assertTrue(plan.regularRemoteCandidates.contains(officialBack))
        assertTrue(plan.communityRemoteCandidates.contains(communityBack))
        assertTrue(plan.allCandidates.indexOf(communityBack) < plan.allCandidates.indexOf("sprites/xyani-back/incineroar.gif"))
    }

    @Test
    fun includesOrasBackSourcesBeforeShowdownFallbacks() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forPlayer("Altaria-Mega", BattleSession.SpriteStyle.MODERN_3D)
        )
        val orasBack = "https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-espalda/altaria-mega.gif"

        assertTrue(plan.regularRemoteCandidates.contains(orasBack))
        assertTrue(plan.allCandidates.indexOf(orasBack) < plan.allCandidates.indexOf("sprites/xyani-back/altariamega.gif"))
    }

    @Test
    fun exposesAnimatedPokeApiFallbacksWithTheCorrectFacing() {
        assertEquals(listOf("shaymin-sky", "shaymin"), ShowdownAssetPaths.pokeApiLookupNames("Shaymin-Sky"))
        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/901.gif",
                "https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/901.gif"
            ),
            ShowdownAssetPaths.hdAnimatedSpriteCandidates(901, BattleSpriteSide.OPPONENT)
        )
        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/901-back.gif"
            ),
            ShowdownAssetPaths.hdAnimatedSpriteCandidates(901, BattleSpriteSide.PLAYER)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/901.gif",
            ShowdownAssetPaths.pokeApiAnimatedSprite(901, BattleSpriteSide.OPPONENT)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/back/901.gif",
            ShowdownAssetPaths.pokeApiAnimatedSprite(901, BattleSpriteSide.PLAYER)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/1006.png",
            ShowdownAssetPaths.pokeApiHighResolutionSprite(1006)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1008.png",
            ShowdownAssetPaths.pokeApiStandardSprite(1008, BattleSpriteSide.PLAYER)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/1008.png",
            ShowdownAssetPaths.pokeApiStandardSprite(1008, BattleSpriteSide.OPPONENT)
        )
    }

    @Test
    fun keepsShinyFrontAndBackArtworkShinyThroughEveryFallbackTier() {
        val front = BattleSpriteRequest.forOpponent("Corviknight", BattleSession.SpriteStyle.MODERN_3D, shiny = true)
        val back = BattleSpriteRequest.forPlayer("Corviknight", BattleSession.SpriteStyle.MODERN_3D, shiny = true)
        val frontCandidates = ShowdownAssetPaths.battleSpriteCandidates(front)
        val backCandidates = ShowdownAssetPaths.battleSpriteCandidates(back)

        assertTrue(frontCandidates.contains("https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/corviknight-s.gif"))
        assertTrue(backCandidates.contains("https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back-s.gif"))
        assertTrue(frontCandidates.contains("sprites/xyani-shiny/corviknight.gif"))
        assertTrue(backCandidates.contains("sprites/xyani-back-shiny/corviknight.gif"))
        assertTrue(backCandidates.none { it.endsWith("/corviknight-back.gif") })
        assertTrue(backCandidates.none { it == "sprites/xyani-back/corviknight.gif" })
    }

    @Test
    fun includesDocumentedGenSixShinyBackRootsAndIndexes() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Altaria-Mega", BattleSession.SpriteStyle.MODERN_3D, shiny = true)
        )
        assertTrue(candidates.contains("https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-espalda-shiny/altaria-mega.gif"))
        assertTrue(candidates.contains("https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda-shiny/altaria.gif"))
        assertTrue(ShowdownAssetPaths.backSpriteIndexUrls(shiny = true).contains("https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon_variocolores_espalda.php"))
        assertTrue(ShowdownAssetPaths.backSpriteIndexUrls(shiny = true).contains("https://www.pkparaiso.com/xy/sprites_pokemon_variocolores_espalda.php"))
    }

    @Test
    fun includesDocumentedGenSixAndSevenShinyFrontRoots() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forOpponent("Altaria", BattleSession.SpriteStyle.MODERN_3D, shiny = true)
        )

        assertTrue(candidates.contains("https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-shiny/altaria.gif"))
        assertTrue(candidates.contains("https://www.pkparaiso.com/imagenes/xy/sprites/animados-shiny/altaria.gif"))
        assertTrue(candidates.contains("https://www.pkparaiso.com/imagenes/sol-luna/sprites/animados-shiny/altaria.gif"))
    }

    @Test
    fun includesDocumentedFrontSpriteIndexPages() {
        assertEquals(
            listOf(
                "https://www.pkparaiso.com/espada_escudo/sprites_pokemon.php",
                "https://www.pkparaiso.com/ultra-sol-ultra-luna/sprites_pokemon_sin_bordes.php",
                "https://www.pkparaiso.com/sol-luna/sprites_pokemon.php",
                "https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon.php",
                "https://www.pkparaiso.com/xy/sprites_pokemon.php"
            ),
            ShowdownAssetPaths.frontSpriteIndexUrls()
        )
        assertTrue(ShowdownAssetPaths.frontSpriteIndexUrls(shiny = true).contains("https://www.pkparaiso.com/xy/sprites_pokemon_variocolores.php"))
    }

    @Test
    fun buildsShinyPokeApiFacingAndHdCandidates() {
        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/901-s.gif",
                "https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/901-s.gif"
            ),
            ShowdownAssetPaths.hdAnimatedSpriteCandidates(901, BattleSpriteSide.OPPONENT, shiny = true)
        )
        assertEquals(
            listOf("https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/901-back-s.gif"),
            ShowdownAssetPaths.hdAnimatedSpriteCandidates(901, BattleSpriteSide.PLAYER, shiny = true)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/showdown/back/shiny/901.gif",
            ShowdownAssetPaths.pokeApiAnimatedSprite(901, BattleSpriteSide.PLAYER, shiny = true)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/shiny/901.png",
            ShowdownAssetPaths.pokeApiHighResolutionSprite(901, shiny = true)
        )
        assertEquals(
            "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/shiny/901.png",
            ShowdownAssetPaths.pokeApiStandardSprite(901, BattleSpriteSide.PLAYER, shiny = true)
        )
    }

    @Test
    fun resolvesHeldItemSpritesAndSkipsUnknownItems() {
        assertEquals("sprites/itemicons/leftovers.png", ShowdownAssetPaths.itemSprite("Leftovers"))
        assertEquals("sprites/itemicons/heavy-duty-boots.png", ShowdownAssetPaths.itemSprite("Heavy-Duty Boots"))
        assertEquals(null, ShowdownAssetPaths.itemSprite("Unknown item"))
        assertEquals(null, ShowdownAssetPaths.itemSprite("No item"))
    }
}
