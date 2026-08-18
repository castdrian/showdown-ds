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
    fun resolvesItemsAndAbilitiesOnRequestTeamSync() {
        val session = BattleSession()
        session.setTeamDetailNameResolvers(
            { it },
            { value -> if (value == "assaultvest") "Assault Vest" else value },
            { value -> if (value == "sapsipper") "Sap Sipper" else value }
        )
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN|",
                "|player|p2|OPPONENT|",
                "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Goodra\",\"details\":\"Goodra, L82, F\",\"condition\":\"265/265\",\"active\":true,\"baseAbility\":\"sapsipper\",\"item\":\"assaultvest\"}]},\"active\":[{}]}"
            )
        )

        assertEquals("Assault Vest", session.playerDetails().item)
        assertEquals("Sap Sipper", session.playerDetails().ability)
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
    fun carriesShinyDetailsIntoTheBattleAndTeamSpriteState() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN|",
                "|player|p2|OPPONENT|",
                "|switch|p1a: Corviknight|Corviknight, L50, shiny|100/100",
                "|switch|p2a: Pikachu|Pikachu, L50|100/100",
                "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Corviknight\",\"details\":\"Corviknight, L50, shiny\",\"condition\":\"100/100\",\"active\":true,\"shiny\":true}]}}"
            )
        )

        assertTrue(session.playerDetails().shiny)
        assertTrue(session.playerActiveCombatants().single().shiny)
        assertTrue(session.playerPartyDetails().single().shiny)
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

    @Test
    fun keepsMegaAndPrimalStonesVisibleAfterGimmickPackets() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN|",
                "|player|p2|OPPONENT|",
                "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Charizard\",\"details\":\"Charizard, L50, M\",\"condition\":\"100/100\"}]}}",
                "|switch|p1a: Charizard|Charizard, L50, M|153/153",
                "|switch|p2a: Kyogre|Kyogre, L50|175/175",
                "|detailschange|p1a: Charizard|Charizard-Mega-X, L50, M",
                "|-mega|p1a: Charizard|Charizardite X",
                "|detailschange|p2a: Kyogre|Kyogre-Primal, L50",
                "|-primal|p2a: Kyogre|Blue Orb",
                "|-mega|p1a: Charizard|[silent]"
            )
        )

        assertEquals("Charizardite X", session.playerDetails().item)
        assertEquals("Blue Orb", session.opponentDetails().item)
        assertEquals("Charizardite X", session.playerPartyDetails().first().item)
        assertTrue(session.opponentPartyDetails().any { it.item == "Blue Orb" })
    }

    @Test
    fun burstFormChangesPreserveNicknamesAndRevealTheBurstItem() {
        val session = BattleSession()
        session.setTeamDetailNameResolvers({ it }, { value -> if (value == "ultranecroziumz") "Ultranecrozium Z" else value }, { it })
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN|",
                "|player|p2|OPPONENT|",
                "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Nebby\",\"details\":\"Necrozma-Dusk-Mane, L50\",\"condition\":\"153/153\",\"active\":true}]}}",
                "|switch|p1a: Nebby|Necrozma-Dusk-Mane, L82, M|153/153",
                "|switch|p2a: Charizard|Charizard, L50|153/153",
                "|-burst|p1a: Nebby|Necrozma-Ultra|ultranecroziumz"
            )
        )

        assertEquals("Nebby", session.playerPokemon)
        assertEquals("Nebby", session.playerDetails().name)
        assertEquals("Necrozma-Ultra", session.playerDetails().species)
        assertEquals("82", session.playerDetails().level)
        assertEquals("♂", session.playerDetails().gender)
        assertEquals("Ultranecrozium Z", session.playerDetails().item)
        assertEquals("Necrozma-Ultra", session.playerPartyDetails().first().species)
        assertEquals("Ultranecrozium Z", session.playerPartyDetails().first().item)
    }
}
