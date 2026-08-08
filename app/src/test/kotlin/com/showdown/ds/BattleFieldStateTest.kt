package com.showdown.ds

import org.junit.Assert.assertEquals
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
}
