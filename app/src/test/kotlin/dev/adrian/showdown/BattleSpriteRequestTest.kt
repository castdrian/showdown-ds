package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSpriteRequestTest {
    @Test
    fun mapsPlayerAndOpponentRequestsToDifferentFacingModes() {
        val player = BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
        val opponent = BattleSpriteRequest.forOpponent("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)

        assertEquals(BattleSpriteSide.PLAYER, player.side)
        assertEquals(true, player.backFacing)
        assertEquals(BattleSpriteSide.OPPONENT, opponent.side)
        assertEquals(false, opponent.backFacing)
        assertNotEquals(player, opponent)
    }

    @Test
    fun plansSinglesAndDoublesWithTheSameSideAwareRequests() {
        val playerCombatants = listOf(
            BattleSession.ActiveCombatant("p2a", "Iron Valiant", emptyList(), "50", "", "100/100", "READY", 0L, species = "Iron Valiant"),
            BattleSession.ActiveCombatant("p2b", "Dragonite", emptyList(), "50", "", "100/100", "READY", 0L, species = "Dragonite")
        )
        val opponentCombatants = listOf(
            BattleSession.ActiveCombatant("p1a", "Tinkaton", emptyList(), "50", "", "100/100", "READY", 0L, species = "Tinkaton")
        )

        val playerSingles = BattleSpriteRequests.single("Iron Valiant", BattleSpriteSide.PLAYER, BattleSession.SpriteStyle.MODERN_3D)
        val playerDoubles = BattleSpriteRequests.active(playerCombatants, BattleSpriteSide.PLAYER, BattleSession.SpriteStyle.MODERN_3D)
        val opponentDoubles = BattleSpriteRequests.active(opponentCombatants, BattleSpriteSide.OPPONENT, BattleSession.SpriteStyle.MODERN_3D)

        assertEquals(true, playerSingles.backFacing)
        assertEquals(listOf("Iron Valiant", "Dragonite"), playerDoubles.map { it.request.species })
        assertEquals(listOf(true, true), playerDoubles.map { it.request.backFacing })
        assertEquals(listOf(false), opponentDoubles.map { it.request.backFacing })
    }

    @Test
    fun playerIronValiantRequestCannotContainAFrontCandidate() {
        val player = BattleSpriteRequest.forPlayer("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)
        val opponent = BattleSpriteRequest.forOpponent("Iron Valiant", BattleSession.SpriteStyle.MODERN_3D)

        val playerCandidates = ShowdownAssetPaths.battleSpriteCandidates(player)
        assertTrue(playerCandidates.contains("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png"))
        assertEquals("sprites/ani-back/substitute.gif", playerCandidates.last())
        assertTrue(playerCandidates.none { it.contains("/FRONT/") })
        assertEquals(
            "sprites/xyani/ironvaliant.gif",
            ShowdownAssetPaths.battleSprite(opponent)
        )
    }

    @Test
    fun modernPlayerPlanNeverUsesGenFiveFallbacks() {
        val plan = ShowdownAssetPaths.battleSpriteResolutionPlan(
            BattleSpriteRequest.forPlayer("Brambleghast", BattleSession.SpriteStyle.MODERN_3D)
        )

        assertEquals("sprites/xyani-back/brambleghast.gif", plan.fallbackCandidates.first())
        assertTrue(plan.fallbackCandidates.none { it.contains("gen5") })
    }

    @Test
    fun modernSpriteCacheNeverFallsBackToLegacyLocalSprites() {
        val source = File("src/main/kotlin/dev/adrian/showdown/ShowdownSpriteCache.kt").readText()

        assertFalse(source.contains("legacyLocalCandidates"))
        assertTrue(source.contains("receiver(standardAsset)"))
        val communityIndex = source.indexOf("requestSpriteCandidates(plan.communityRemoteCandidates)")
        val regularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", source.indexOf("fun requestModernLocalOrRegular()"))
        val modernHdIndex = source.indexOf("requestPokeApiModernHdSprite(request)")
        val animatedFallbackIndex = source.indexOf("requestPokeApiAnimatedSprite(request)")
        val homeIndex = source.indexOf("requestPokeApiHighResolutionSprite(request)")
        val verifiedIndex = source.indexOf("requestSpriteCandidates(plan.verifiedRemoteCandidates)")
        val localFallbackIndex = source.indexOf("requestModernLocalOrRegular()")
        assertTrue(communityIndex >= 0)
        assertTrue(modernHdIndex < communityIndex)
        assertTrue(localFallbackIndex > modernHdIndex)
        assertTrue(regularIndex > localFallbackIndex)
        assertTrue(animatedFallbackIndex > regularIndex)
        assertTrue(localFallbackIndex >= 0)
        assertTrue(verifiedIndex >= 0)
        assertTrue(homeIndex > verifiedIndex)
        assertTrue(source.indexOf("ShowdownAssetPaths.hdAnimatedSpriteCandidates") < source.indexOf("ShowdownAssetPaths.pokeApiAnimatedSprite"))
    }
}
