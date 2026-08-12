package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
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

        assertEquals(
            listOf(
                "https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/back/1006.png",
                "sprites/ani-back/substitute.gif"
            ),
            ShowdownAssetPaths.battleSpriteCandidates(player)
        )
        assertEquals(
            "sprites/xyani/ironvaliant.gif",
            ShowdownAssetPaths.battleSprite(opponent)
        )
    }
}
