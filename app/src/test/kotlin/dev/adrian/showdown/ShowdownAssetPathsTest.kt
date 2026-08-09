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
            listOf("sprites/xyani/furfroulareine.gif", "sprites/xyani/furfrou.gif"),
            ShowdownAssetPaths.battleSpriteCandidates("Furfrou-La Reine", false, BattleSession.SpriteStyle.MODERN_3D)
        )
    }
}
