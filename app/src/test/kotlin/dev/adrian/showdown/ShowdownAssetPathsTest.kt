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
        assertTrue(candidates.take(candidates.size - 10).all { it.startsWith("https://www.pkparaiso.com/") })
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
        assertTrue(candidates.dropLast(6).all { it.startsWith("https://www.pkparaiso.com/") })
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
        assertTrue(candidates.dropLast(2).all { it.startsWith("https://") })
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png",
                "sprites/ani-back/substitute.gif"
            ),
            candidates.takeLast(2)
        )
    }

    @Test
    fun keepsIronValiantFrontAndBackCandidatesSeparate() {
        val frontCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forOpponent("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))
        val backCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))

        assertTrue(frontCandidates.contains("sprites/gen5ani/ironvaliant.gif"))
        assertFalse(frontCandidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertTrue(backCandidates.dropLast(2).all { it.startsWith("https://") })
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png",
                "sprites/ani-back/substitute.gif"
            ),
            backCandidates.takeLast(2)
        )
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
    fun probesSeparatePkParaisoHdBackRootsBeforeLegacyBackArtwork() {
        val candidates = ShowdownAssetPaths.battleSpriteCandidates(
            BattleSpriteRequest.forPlayer("Uxie", BattleSession.SpriteStyle.MODERN_3D)
        )
        val hdCandidate = candidates.first { it.endsWith("/uxie.gif") && it.contains("animados-espalda") }
        assertTrue(hdCandidate.startsWith("https://www.pkparaiso.com/"))
        assertTrue(candidates.indexOf(hdCandidate) < candidates.indexOf("sprites/xyani-back/uxie.gif"))
    }

    @Test
    fun exposesAnimatedPokeApiFallbacksWithTheCorrectFacing() {
        assertEquals(listOf("shaymin-sky", "shaymin"), ShowdownAssetPaths.pokeApiLookupNames("Shaymin-Sky"))
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
    }
}
