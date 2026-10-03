package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFieldStateTest {
    @Test
    fun parsesSideConditionsFromTheOfficialCombinedSideField() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-sidestart|p1: Stealth Rock",
                "|-sidestart|p2: Spikes",
                "|-sideend|p2: Spikes"
            )
        )

        assertEquals(listOf("Stealth Rock"), session.battleInfo().playerSideConditions)
        assertEquals(emptyList<String>(), session.battleInfo().opponentSideConditions)
    }

    @Test
    fun tracksRoomEffectsAlongsideTerrain() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-fieldstart|move: Electric Terrain",
                "|-fieldstart|move: Trick Room",
                "|-fieldstart|move: Gravity",
                "|-fieldend|move: Trick Room"
            )
        )

        assertEquals("Electric Terrain", session.battleInfo().terrain)
        assertEquals(listOf("Gravity"), session.battleInfo().fieldEffects)
    }

    @Test
    fun treatsTripleBattleCenteringAsVisualStateInsteadOfLogText() {
        val session = BattleSession()
        session.applyProtocolLine("|gametype|triples")
        val messagesBeforeCentering = session.battleLog()

        session.applyProtocolLine("|-center")

        assertTrue(session.isTriplesCentered())
        assertEquals(messagesBeforeCentering, session.battleLog())

        session.applyProtocolLine("|init|battle")

        assertFalse(session.isTriplesCentered())
    }

    @Test
    fun keepsTheOfficialBattleClockUntilTheServerTurnsItOff() {
        val session = BattleSession()

        session.applyProtocolLine("|inactive|Time left: 150 sec this turn | 150 sec total | 60 sec grace")

        assertEquals(BattleSession.BattleClock(150, 150, 60), session.battleClock())
        assertTrue(session.battleClockSeconds()!! in 149..150)
        assertFalse(session.battleLog().any { it.contains("Time left") })

        session.applyProtocolLine("|inactiveoff|")

        assertEquals(null, session.battleClock())
    }

    @Test
    fun formatsWeatherAnnouncementsDuringUpkeep() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-weather|RainDance",
                "|-weather|RainDance|[upkeep]",
                "|-activate|move: Splash"
            )
        )

        assertEquals("RainDance", session.battleInfo().weather)
        assertEquals(1, session.battleLog().count { it == "It started to rain!" })
        assertEquals(1, session.battleLog().count { it == "(Rain continues to fall.)" })
        assertTrue(session.battleLog().contains("Splash activated."))
    }

    @Test
    fun clearsOpponentTeamPreviewEntriesWhenTheServerResetsPreview() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|poke|p2|Garchomp, L50|item",
                "|clearpoke"
            )
        )

        assertTrue(session.opponentPartyDetails().isEmpty())
    }

    @Test
    fun keepsOpponentTeamPreviewEntriesForTheBattleScreen() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|gametype|doubles",
                "|teampreview|2",
                "|poke|p2|Garchomp, L50|",
                "|poke|p2|Rotom-Wash, L50|"
            )
        )

        assertEquals(BattleSession.BattlePhase.TEAM_PREVIEW, session.battlePhase)
        assertEquals(listOf("Garchomp", "Rotom-Wash"), session.opponentPartyDetails().map { it.species })
    }

    @Test
    fun opponentTeamCardsKeepAvailabilityAndFaintedStateAfterBattleStarts() {
        val session = BattleSession()
        session.setSpectatorMode(true)

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|poke|p2|Garchomp, L50|",
                "|poke|p2|Rotom-Wash, L50|",
                "|switch|p2a: Garchomp|Garchomp, L50|100/100",
                "|-damage|p2a: Garchomp|0 fnt",
                "|faint|p2a: Garchomp"
            )
        )

        assertEquals("Fainted", session.opponentTeamCardStatus(0))
        assertEquals("Available", session.opponentTeamCardStatus(1))

        session.applyProtocolPacket(listOf("|switch|p2a: Rotom-Wash|Rotom-Wash, L50|100/100"))

        assertEquals("In battle", session.opponentTeamCardStatus(1))
    }
}
