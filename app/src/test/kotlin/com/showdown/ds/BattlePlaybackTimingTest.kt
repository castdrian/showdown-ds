package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Test

class BattlePlaybackTimingTest {
    @Test
    fun keepsMoveConsequencesTogetherBeforeTheNextAction() {
        val lines = listOf(
            "|turn|1",
            "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
            "|-damage|p2a: Gyarados|120/200",
            "|-supereffective|p2a: Gyarados",
            "|move|p2a: Gyarados|Earthquake|p1a: Pikachu",
            "|-damage|p1a: Pikachu|0 fnt",
            "|faint|p1a: Pikachu",
            "|request|{}"
        )

        assertEquals(
            listOf(
                listOf("|turn|1"),
                listOf(
                    "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
                    "|-damage|p2a: Gyarados|120/200",
                    "|-supereffective|p2a: Gyarados"
                ),
                listOf(
                    "|move|p2a: Gyarados|Earthquake|p1a: Pikachu",
                    "|-damage|p1a: Pikachu|0 fnt",
                    "|faint|p1a: Pikachu"
                ),
                listOf("|request|{}")
            ),
            BattlePlaybackTiming.chunks(lines)
        )
    }

    @Test
    fun givesFaintsLongerReadingTimeThanOrdinaryMoves() {
        assertEquals(700L, BattlePlaybackTiming.pauseAfter(listOf("|move|p1a: Pikachu|Tackle|p2a: Eevee")))
        assertEquals(950L, BattlePlaybackTiming.pauseAfter(listOf("|move|p1a: Pikachu|Tackle|p2a: Eevee", "|faint|p2a: Eevee")))
    }

    @Test
    fun doesNotDelayUnrelatedProtocolMetadata() {
        assertEquals(0L, BattlePlaybackTiming.pauseAfter(listOf("|-weather|SunnyDay", "|-fieldstart|move: Electric Terrain")))
    }
}
