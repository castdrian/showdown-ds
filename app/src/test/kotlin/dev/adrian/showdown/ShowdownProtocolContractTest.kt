package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownProtocolContractTest {
    @Test
    fun preservesTheLocalPlayersPerspectiveForPlayerTwoAndTracksRevealedState() {
        val session = BattleSession()
        session.setLocalUsername("ADRIAN")

        session.applyProtocolPacket(
            listOf(
                "|player|p1|OPPONENT|",
                "|player|p2|ADRIAN|",
                "|switch|p1a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p2a: Rotom-Wash|Rotom-Wash, L50|83/100 brn",
                "|-ability|p2a: Rotom-Wash|Levitate",
                "|-item|p2a: Rotom-Wash|Leftovers",
                "|-damage|p2a: Rotom-Wash|0 fnt",
                "|faint|p2a: Rotom-Wash"
            )
        )

        assertEquals("ADRIAN", session.playerName)
        assertEquals("OPPONENT", session.opponentName)
        assertEquals("Rotom-Wash", session.playerPokemon)
        assertEquals("0 fnt", session.playerHp)
        assertEquals("Levitate", session.playerDetails().ability)
        assertEquals("Leftovers", session.playerDetails().item)
        assertTrue(session.battleLog().last().contains("Rotom-Wash fainted"))
    }

    @Test
    fun appliesStatusesFormChangesAndTerastallizationToTheVisibleBattleState() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN|",
                "|player|p2|OPPONENT|",
                "|switch|p1a: Charizard|Charizard, L50, M|153/153",
                "|switch|p2a: Dragapult|Dragapult, L50|163/163",
                "|-status|p1a: Charizard|brn",
                "|detailschange|p1a: Charizard|Charizard-Mega-X, L50, M",
                "|-terastallize|p2a: Dragapult|Ghost",
                "|-curestatus|p1a: Charizard|brn"
            )
        )

        assertEquals("Charizard-Mega-X", session.playerPokemon)
        assertEquals("Charizard-Mega-X", session.playerDetails().name)
        assertEquals("READY", session.playerCondition)
        assertEquals(listOf("GHOST"), session.opponentDetails().types)
    }

    @Test
    fun appliesHpAndStatusCarriedByOfficialFormChangePackets() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN|",
                "|player|p2|OPPONENT|",
                "|switch|p1a: Charizard|Charizard, L50, M|153/153",
                "|switch|p2a: Dragapult|Dragapult, L50|163/163",
                "|detailschange|p1a: Charizard|Charizard-Mega-X, L50, M|83/153 brn",
                "|-formechange|p2a: Dragapult|Dragapult-Tera|41/163 par"
            )
        )

        assertEquals("83/153 brn", session.playerHp)
        assertEquals("BRN", session.playerCondition)
        assertEquals("Charizard-Mega-X", session.playerDetails().name)
        assertEquals("41/163 par", session.opponentHp)
        assertEquals("PAR", session.opponentCondition)
        assertEquals("Dragapult-Tera", session.opponentDetails().name)
    }
}
