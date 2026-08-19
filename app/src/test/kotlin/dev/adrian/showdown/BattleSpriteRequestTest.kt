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
    fun carriesShinyStateIntoEveryActiveSpriteRequest() {
        val active = BattleSession.ActiveCombatant(
            "p1a",
            "Corviknight",
            emptyList(),
            "50",
            "",
            "100/100",
            "READY",
            0L,
            species = "Corviknight",
            shiny = true
        )

        val request = BattleSpriteRequests.active(listOf(active), BattleSpriteSide.PLAYER, BattleSession.SpriteStyle.MODERN_3D).single().request

        assertTrue(request.backFacing)
        assertTrue(request.shiny)
        assertTrue(ShowdownAssetPaths.battleSpriteCandidates(request).any { it.endsWith("corviknight-back-s.gif") })
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
        assertTrue(playerCandidates.none { it.endsWith(".png") })
        assertEquals("sprites/xyani-back/ironvaliant.gif", playerCandidates.last())
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
        assertTrue(source.contains("requestPokeApiAnimatedSprite(request, receiver)"))
        assertFalse(source.contains("requestPokeApiStandardSprite"))
        assertFalse(source.contains("requestPokeApiHighResolutionSprite"))
        val animatedFallbackIndex = source.indexOf(
            "requestPokeApiAnimatedSprite(request)",
            source.indexOf("private fun requestSmallSpriteResolution")
        )
        val backIndex = source.indexOf("private fun requestBackSpriteResolution")
        val scrapedBackIndex = source.indexOf("requestScrapedBackSpriteResolution(request, highResolutionOnly = true)", backIndex)
        val scrapedBackRegularIndex = source.indexOf("requestScrapedBackSpriteResolution(request, highResolutionOnly = false)", backIndex)
        val backRegularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", backIndex)
        val backCommunityIndex = source.indexOf("requestAnimatedSpriteCandidates(plan.communityRemoteCandidates)", backIndex)
        val frontIndex = source.indexOf("private fun requestFrontSpriteResolution")
        val frontHdIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = true)", frontIndex)
        val frontAnimatedIndex = source.indexOf("requestScrapedFrontSpriteResolution(request, highResolutionOnly = false)", frontIndex)
        val communityIndex = source.indexOf("requestAnimatedSpriteCandidates(plan.communityRemoteCandidates)", frontIndex)
        val regularIndex = source.indexOf("requestRegularRemoteSpriteResolution(plan)", frontIndex)
        val localFallbackIndex = source.indexOf("requestAnimatedSpriteCandidates(modernLocalCandidates)")
        assertTrue(backRegularIndex >= 0)
        assertTrue(scrapedBackIndex >= 0)
        assertTrue(scrapedBackIndex < backRegularIndex)
        assertTrue(scrapedBackRegularIndex > backCommunityIndex)
        assertTrue(backCommunityIndex > scrapedBackIndex)
        assertTrue(frontHdIndex >= 0)
        assertTrue(frontAnimatedIndex > frontHdIndex)
        assertTrue(communityIndex >= 0)
        assertTrue(communityIndex < regularIndex)
        assertTrue(regularIndex < frontAnimatedIndex)
        assertTrue(animatedFallbackIndex > localFallbackIndex)
        assertTrue(localFallbackIndex >= 0)
        assertFalse(source.contains("requestPokeApiModernHdSprite"))
        assertFalse(source.contains("hdAnimatedSpriteCandidates"))
    }
}
