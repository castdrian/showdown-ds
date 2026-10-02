package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownReplayIdentityTest {
    @Test
    fun keepsOfficialNicknameInBattleLogAndSpeciesInSpriteRequest() {
        val session = BattleSession().apply { setLocalUsername("DoerreKong") }
        session.applyProtocolPacket(
            listOf(
                "|gametype|doubles",
                "|player|p1|DoerreKong||",
                "|player|p2|froonk53||",
                "|switch|p1a: Dragapult|Dragapult, F|100/100",
                "|switch|p1b: Solgaleo|Solgaleo|100/100",
                "|switch|p2a: Grimmsnarl|Grimmsnarl, M, shiny|100/100",
                "|switch|p2b: Rotom|Rotom-Wash, shiny|100/100",
                "|move|p2b: Rotom|Hex|p1a: Dragapult"
            )
        )

        val rotom = session.opponentActiveCombatants().single { it.slot == "p2b" }
        val spriteRequest = BattleSpriteRequests.active(
            session.opponentActiveCombatants(),
            BattleSpriteSide.OPPONENT,
            BattleSession.SpriteStyle.MODERN_3D
        ).single { it.slot == "p2b" }.request

        assertEquals("Rotom", rotom.name)
        assertEquals("Rotom-Wash", rotom.species)
        assertEquals("Rotom-Wash", spriteRequest.species)
        assertEquals(BattleSpriteSide.OPPONENT, spriteRequest.side)
        assertTrue(session.battleLog().contains("Rotom used Hex!"))
    }
}
