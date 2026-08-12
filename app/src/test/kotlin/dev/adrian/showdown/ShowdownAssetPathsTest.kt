package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownAssetPathsTest {
    @Test
    fun usesShowdownsDistinctDexAndAnimatedSpriteIdentifiers() {
        assertEquals("sprites/dex/rotom-wash.png", ShowdownAssetPaths.dexSprite("Rotom-Wash"))
        assertEquals("sprites/xyani/rotomwash.gif", ShowdownAssetPaths.battleSprite("Rotom-Wash", false, BattleSession.SpriteStyle.MODERN_3D))
        assertEquals("sprites/xyani-back/rotomwash.gif", ShowdownAssetPaths.battleSprite("Rotom-Wash", true, BattleSession.SpriteStyle.MODERN_3D))
    }

    @Test
    fun keepsHyphenatedDexFormsAndNormalizesAnimatedSprites() {
        assertEquals("sprites/dex/ho-oh.png", ShowdownAssetPaths.dexSprite("Ho-Oh"))
        assertEquals("sprites/dex/nidoran-m.png", ShowdownAssetPaths.dexSprite("Nidoran♂"))
        assertEquals("sprites/gen5ani/hooh.gif", ShowdownAssetPaths.battleSprite("Ho-Oh", false, BattleSession.SpriteStyle.CLASSIC_2D))
    }

    @Test
    fun fallsBackToTheBaseSpeciesForUnavailableFormSprites() {
        assertEquals(
            listOf(
                "sprites/xyani/furfroulareine.gif",
                "sprites/xyani/furfrou.gif",
                "sprites/gen5ani/furfroulareine.gif",
                "sprites/gen5ani/furfrou.gif",
                "sprites/dex/furfrou-la-reine.png",
                "sprites/dex/furfroulareine.png"
            ),
            ShowdownAssetPaths.battleSpriteCandidates("Furfrou-La Reine", false, BattleSession.SpriteStyle.MODERN_3D)
        )
    }

    @Test
    fun fallsBackToGen5AnimationAndDexSpritesForNewSpecies() {
        assertEquals(
            listOf(
                "sprites/xyani/ironhands.gif",
                "sprites/gen5ani/ironhands.gif",
                "sprites/dex/iron-hands.png",
                "sprites/dex/ironhands.png"
            ),
            ShowdownAssetPaths.battleSpriteCandidates("Iron Hands", false, BattleSession.SpriteStyle.MODERN_3D)
        )
    }

    @Test
    fun neverFallsBackFromAPlayerBackSpriteToAFrontSprite() {
        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png",
                "sprites/gen5-back/ironvaliant.png",
                "sprites/xyani-back/ironvaliant.gif",
                "sprites/gen5ani-back/ironvaliant.gif",
                "sprites/xy-back/ironvaliant.png",
                "sprites/ani-back/substitute.gif"
            ),
            ShowdownAssetPaths.battleSpriteCandidates("Iron Valiant", true, BattleSession.SpriteStyle.MODERN_3D)
        )
    }
}
