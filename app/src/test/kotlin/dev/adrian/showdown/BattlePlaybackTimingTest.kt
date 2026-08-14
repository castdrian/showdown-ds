package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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
        assertEquals(2_600L, BattlePlaybackTiming.pauseAfter(listOf("|move|p1a: Pikachu|Tackle|p2a: Eevee")))
        assertEquals(4_800L, BattlePlaybackTiming.pauseAfter(listOf("|move|p1a: Pikachu|Tackle|p2a: Eevee", "|faint|p2a: Eevee")))
    }

    @Test
    fun givesMultiLineMoveResultsEnoughTimeToReadEachLine() {
        assertEquals(
            7_200L,
            BattlePlaybackTiming.pauseAfter(
                listOf(
                    "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
                    "|-damage|p2a: Gyarados|120/200",
                    "|-supereffective|p2a: Gyarados"
                )
            )
        )
    }

    @Test
    fun identifiesDecisionChunksForImmediateLiveControls() {
        assertTrue(BattlePlaybackTiming.isDecisionChunk(listOf("|request|{}")))
        assertFalse(BattlePlaybackTiming.isDecisionChunk(listOf("|turn|1")))
    }

    @Test
    fun doesNotDelayUnrelatedProtocolMetadata() {
        assertEquals(0L, BattlePlaybackTiming.pauseAfter(listOf("|gen|9", "|tier|[Gen 9] OU")))
    }

    @Test
    fun scalesReplayPausesWithoutChangingLiveTiming() {
        assertEquals(1_300L, BattlePlaybackTiming.scaledPause(2_600L, 2f))
        assertEquals(5_200L, BattlePlaybackTiming.scaledPause(2_600L, 0.5f))
        assertEquals(2_600L, BattlePlaybackTiming.scaledPause(2_600L, 1f))
    }
}
