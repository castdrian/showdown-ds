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
    fun keepsHyphenatedDexFormsAndNormalizesAnimatedSprites() {
        assertEquals("sprites/dex/ho-oh.png", ShowdownAssetPaths.dexSprite("Ho-Oh"))
        assertEquals("sprites/dex/nidoran-m.png", ShowdownAssetPaths.dexSprite("Nidoran♂"))
        assertEquals("sprites/gen5ani/hooh.gif", ShowdownAssetPaths.battleSprite(BattleSpriteRequest.forOpponent("Ho-Oh", BattleSession.SpriteStyle.CLASSIC_2D)))
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
                "sprites/gen5ani/furfroulareine.gif",
                "sprites/gen5ani/furfrou.gif",
                "sprites/xy/furfroulareine.png",
                "sprites/xy/furfrou.png",
                "sprites/gen5/furfroulareine.png",
                "sprites/gen5/furfrou.png",
                "sprites/dex/furfrou-la-reine.png",
                "sprites/dex/furfroulareine.png"
            ),
            candidates.takeLast(10)
        )
        assertTrue(candidates.take(candidates.size - 10).all { it.startsWith("https://") })
    }

    @Test
    fun fallsBackToGen5AnimationAndDexSpritesForNewSpecies() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forOpponent("Iron Hands", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertEquals(
            listOf(
                "sprites/xyani/ironhands.gif",
                "sprites/gen5ani/ironhands.gif",
                "sprites/xy/ironhands.png",
                "sprites/gen5/ironhands.png",
                "sprites/dex/iron-hands.png",
                "sprites/dex/ironhands.png"
            ),
            candidates.takeLast(6)
        )
        assertTrue(candidates.dropLast(6).all { it.startsWith("https://") })
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
        assertTrue(plan.fallbackCandidates.contains("sprites/gen5ani/ironhands.gif"))
    }

    @Test
    fun classicResolutionPlanDoesNotSilentlyPreferHdAssets() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forOpponent("Alcremie", BattleSession.SpriteStyle.CLASSIC_2D)
        )
        assertFalse(plan.usesModernAnimatedFallback)
        assertTrue(plan.preferredRemoteCandidates.isEmpty())
        assertTrue(plan.fallbackCandidates.first().contains("sprites/gen5ani/alcremie.gif"))
    }

    @Test
    fun fallsBackToStaticGen5FrontSpritesForSpeciesWithoutAnimatedAssets() {
        assertTrue(
            ShowdownAssetPaths.battleSpriteCandidates(
                BattleSpriteRequest.forOpponent("Pecharunt", BattleSession.SpriteStyle.MODERN_3D)
            ).contains("sprites/gen5/pecharunt.png")
        )
    }

    @Test
    fun neverFallsBackFromAPlayerBackSpriteToAFrontSprite() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
        )
        assertTrue(candidates.none { it.contains("/FRONT/") })
        assertTrue(candidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertEquals("sprites/ani-back/substitute.gif", candidates.last())
    }

    @Test
    fun keepsIronValiantFrontAndBackCandidatesSeparate() {
        val frontCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forOpponent("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))
        val backCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))

        assertTrue(frontCandidates.contains("sprites/gen5ani/ironvaliant.gif"))
        assertFalse(frontCandidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertTrue(backCandidates.none { it.contains("/FRONT/") })
        assertTrue(backCandidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertEquals("sprites/ani-back/substitute.gif", backCandidates.last())
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
    fun probesGenerationSixAndSevenBackAnimationsBeforePixelFallbacks() {
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
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/901-back.gif",
                "https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/901-back.gif"
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
    fun resolvesHeldItemSpritesAndSkipsUnknownItems() {
        assertEquals("sprites/itemicons/leftovers.png", ShowdownAssetPaths.itemSprite("Leftovers"))
        assertEquals("sprites/itemicons/heavy-duty-boots.png", ShowdownAssetPaths.itemSprite("Heavy-Duty Boots"))
        assertEquals(null, ShowdownAssetPaths.itemSprite("Unknown item"))
        assertEquals(null, ShowdownAssetPaths.itemSprite("No item"))
    }
}
