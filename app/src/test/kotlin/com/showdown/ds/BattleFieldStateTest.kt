package com.showdown.ds

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
    fun keepsTheOfficialBattleClockUntilTheServerTurnsItOff() {
        val session = BattleSession()

        session.applyProtocolLine("|inactive|Time left: 150 sec this turn | 150 sec total | 60 sec grace")

        assertEquals(BattleSession.BattleClock(150, 150, 60), session.battleClock())
        assertTrue(session.battleClockSeconds()!! in 149..150)
        assertFalse(session.battleLog().any { it.contains("Time left") })

        session.applyProtocolLine("|inactiveoff|")

        assertEquals(null, session.battleClock())
    }
}
