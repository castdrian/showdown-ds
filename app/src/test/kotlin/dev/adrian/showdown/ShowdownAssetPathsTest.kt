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
            ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forOpponent("Furfrou-La Reine", BattleSession.SpriteStyle.MODERN_3D))
        )
    }

    @Test
    fun fallsBackToGen5AnimationAndDexSpritesForNewSpecies() {
        assertEquals(
            listOf(
                "sprites/xyani/ironhands.gif",
                "sprites/gen5ani/ironhands.gif",
                "sprites/xy/ironhands.png",
                "sprites/gen5/ironhands.png",
                "sprites/dex/iron-hands.png",
                "sprites/dex/ironhands.png"
            ),
            ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forOpponent("Iron Hands", BattleSession.SpriteStyle.MODERN_3D))
        )
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
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png",
                "sprites/ani-back/substitute.gif"
            ),
            ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))
        )
    }

    @Test
    fun keepsIronValiantFrontAndBackCandidatesSeparate() {
        val frontCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forOpponent("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))
        val backCandidates = ShowdownAssetPaths.battleSpriteCandidates(BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D))

        assertTrue(frontCandidates.contains("sprites/gen5ani/ironvaliant.gif"))
        assertFalse(frontCandidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png",
                "sprites/ani-back/substitute.gif"
            ),
            backCandidates
        )
    }
}
