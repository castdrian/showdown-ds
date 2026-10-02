package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownReplayIdentityTest {
    @Test
    fun formatsOpponentNamesWithShowdownPerspective() {
        val session = BattleSession().apply { setLocalUsername("DoerreKong") }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong||",
                "|player|p2|froonk53||",
                "|move|p2a: Rotom|Hex|p1a: Pikachu"
            )
        )

        assertEquals("The opposing Rotom used Hex!", session.battleLog().last())
        session.applyProtocolPacket(listOf("|faint|p2a: Rotom"))
        assertEquals("The opposing Rotom fainted!", session.battleLog().last())
    }

    @Test
    fun formatsNamesFromTheP2Perspective() {
        val session = BattleSession().apply { setLocalUsername("froonk53") }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong||",
                "|player|p2|froonk53||",
                "|move|p2a: Rotom|Hex|p1a: Pikachu"
            )
        )

        assertEquals("Rotom used Hex!", session.battleLog().last())
        session.applyProtocolPacket(listOf("|move|p1a: Pikachu|Tackle|p2a: Rotom"))
        assertEquals("The opposing Pikachu used Tackle!", session.battleLog().last())
    }

    @Test
    fun formatsSpectatorNamesWithTrainerNames() {
        val session = BattleSession().apply { setSpectatorMode(true) }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|Red||",
                "|player|p2|Blue||",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|move|p2a: Eevee|Tackle|p1a: Pikachu"
            )
        )

        assertTrue(session.battleLog().contains("Red sent out Pikachu!"))
        assertTrue(session.battleLog().contains("Blue sent out Eevee!"))
        assertEquals("Blue's Eevee used Tackle!", session.battleLog().last())
    }

    @Test
    fun usesEachTrainersNameForMultiBattleSwitches() {
        val session = BattleSession().apply { setLocalUsername("Red") }
        session.applyProtocolPacket(
            listOf(
                "|gametype|multi",
                "|player|p1|Red||",
                "|player|p2|Blue||",
                "|player|p3|Green||",
                "|player|p4|Yellow||",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|switch|p3a: AllyChu|Pikachu, L50|100/100",
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|switch|p4a: Pidgey|Pidgey, L50|100/100"
            )
        )

        assertTrue(session.battleLog().contains("Go! Pikachu!"))
        assertTrue(session.battleLog().contains("Green sent out AllyChu (Pikachu)!"))
        assertTrue(session.battleLog().contains("Blue sent out Eevee!"))
        assertTrue(session.battleLog().contains("Yellow sent out Pidgey!"))
    }

    @Test
    fun keepsOfficialReplayNicknameAlignedWithDisplayedSpecies() {
        val session = BattleSession().apply { setLocalUsername("DoerreKong") }
        session.setReplayMode(true)
        val replayExcerpt = listOf(
            "|player|p1|DoerreKong|156|1510",
            "|player|p2|froonk53|101|1189",
            "|gametype|doubles",
            "|switch|p1a: Dragapult|Dragapult, F|100/100",
            "|switch|p1b: Solgaleo|Solgaleo|100/100",
            "|switch|p2a: Grimmsnarl|Grimmsnarl, M, shiny|100/100",
            "|switch|p2b: Rotom|Rotom-Wash, shiny|100/100",
            "|move|p2b: Rotom|Hex|p1a: Dragapult"
        )
        BattlePlaybackTiming.chunks(replayExcerpt).forEach(session::applyProtocolPacket)

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
        assertEquals(
            "froonk53 sent out Rotom (Rotom-Wash)!",
            session.battleLog().first { it.contains("sent out Rotom") }
        )
        assertTrue(session.battleLog().contains("The opposing Rotom used Hex!"))
        assertEquals("The opposing Rotom used Hex!", session.latestMoveEvent)
    }

    @Test
    fun updatesPlayerReplayRosterNameWhenSwitchRevealsNickname() {
        val session = BattleSession().apply {
            setLocalUsername("DoerreKong")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong|156|1510",
                "|player|p2|froonk53|101|1189",
                "|poke|p1|Rotom-Wash|",
                "|switch|p1a: Rotom|Rotom-Wash, L50|100/100"
            )
        )

        assertEquals(listOf("Rotom"), session.team())
        assertEquals("Rotom", session.playerPartyDetails().single().name)
        assertEquals("Rotom-Wash", session.playerPartyDetails().single().species)
        assertEquals("Rotom", session.playerActiveCombatants().single().name)
    }

    @Test
    fun disambiguatesSharedReplayNicknamesBySpecies() {
        val session = BattleSession().apply {
            setLocalUsername("DoerreKong")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|DoerreKong|156|1510",
                "|player|p2|froonk53|101|1189",
                "|poke|p1|Pikachu|",
                "|poke|p1|Raichu|",
                "|switch|p1a: Sparky|Pikachu, L50|100/100",
                "|switch|p1a: Sparky|Raichu, L50|100/100"
            )
        )

        assertEquals(listOf("Pikachu", "Raichu"), session.playerPartyDetails().map { it.species })
        assertEquals(listOf("Sparky", "Sparky"), session.team())
        assertEquals("Raichu", session.playerActiveCombatants().single().species)
    }
}
