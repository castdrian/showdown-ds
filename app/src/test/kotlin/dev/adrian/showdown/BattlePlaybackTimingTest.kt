package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlaybackTimingTest {
    @Test
    fun defersMessageBarClearUntilAfterTheActionDwell() {
        val lines = listOf(
            "|move|p2a: Hypno|Toxic|p1a: Registeel",
            "|-immune|p1a: Registeel",
            "|",
            "|switch|p2a: Suicune|Suicune, L82|298/298"
        )

        assertEquals(
            listOf(
                listOf(
                    "|move|p2a: Hypno|Toxic|p1a: Registeel",
                    "|-immune|p1a: Registeel"
                ),
                listOf("|"),
                listOf("|switch|p2a: Suicune|Suicune, L82|298/298")
            ),
            BattlePlaybackTiming.chunks(lines)
        )
    }

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
                    "|-damage|p1a: Pikachu|0 fnt"
                ),
                listOf("|faint|p1a: Pikachu"),
                listOf("|request|{}")
            ),
            BattlePlaybackTiming.chunks(lines)
        )
    }

    @Test
    fun isolatesSpeciesChangingEventsFromTheActionTheyFollow() {
        val move = "|move|p1a: Cramorant|Surf|p2a: Perrserker"
        listOf(
            "|detailschange|p1a: Charizard|Charizard-Mega-X, L50|153/153",
            "|-formechange|p1a: Cramorant|Cramorant-Gulping|",
            "|-transform|p1a: Ditto|p2a: Dragapult",
            "|-burst|p1a: Necrozma|Necrozma-Ultra|Ultranecrozium Z",
            "|-mega|p1a: Charizard|Charizardite X",
            "|-primal|p1a: Kyogre|Blue Orb",
            "|-candynamax|p1a: Charizard"
        ).forEach { transition ->
            assertEquals(
                listOf(listOf(move), listOf(transition)),
                BattlePlaybackTiming.chunks(listOf(move, transition))
            )
        }

        assertEquals(
            listOf(
                listOf(move),
                listOf(
                    "|-formechange|p1a: Cramorant|Cramorant-Gulping|",
                    "|-damage|p2a: Perrserker|155/269"
                ),
                listOf("|")
            ),
            BattlePlaybackTiming.chunks(
                listOf(
                    move,
                    "|-formechange|p1a: Cramorant|Cramorant-Gulping|",
                    "|-damage|p2a: Perrserker|155/269",
                    "|"
                )
            )
        )
    }

    @Test
    fun isolatesTerastallizationBetweenMovesInTheSameTurn() {
        val lines = listOf(
            "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
            "|-damage|p2a: Gyarados|120/200",
            "|-terastallize|p2a: Gyarados|WATER",
            "|move|p2a: Gyarados|Earthquake|p1a: Pikachu"
        )

        assertEquals(
            listOf(
                listOf(
                    "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
                    "|-damage|p2a: Gyarados|120/200"
                ),
                listOf("|-terastallize|p2a: Gyarados|WATER"),
                listOf("|move|p2a: Gyarados|Earthquake|p1a: Pikachu")
            ),
            BattlePlaybackTiming.chunks(lines)
        )
    }

    @Test
    fun isolatesEndOfTurnResidualChangesFromThePrecedingMove() {
        val lines = listOf(
            "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
            "|-damage|p2a: Gyarados|120/200",
            "|upkeep",
            "|-weather|none",
            "|-heal|p2a: Gyarados|130/200|[from] item: Leftovers",
            "|turn|2"
        )

        assertEquals(
            listOf(
                listOf(
                    "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
                    "|-damage|p2a: Gyarados|120/200"
                ),
                listOf(
                    "|upkeep",
                    "|-weather|none",
                    "|-heal|p2a: Gyarados|130/200|[from] item: Leftovers"
                ),
                listOf("|turn|2")
            ),
            BattlePlaybackTiming.chunks(lines)
        )
    }

    @Test
    fun isolatesMajorBattleMessagesAndFaintsBetweenActions() {
        val precedingMove = listOf(
            "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados",
            "|-damage|p2a: Gyarados|120/200"
        )
        listOf(
            "|cant|p2a: Gyarados|par",
            "|start",
            "|-candynamax|p2a: Gyarados"
        ).forEach { majorMessage ->
            assertEquals(
                listOf(precedingMove, listOf(majorMessage)),
                BattlePlaybackTiming.chunks(precedingMove + majorMessage)
            )
        }

        assertEquals(
            listOf(precedingMove, listOf("|faint|p2a: Gyarados")),
            BattlePlaybackTiming.chunks(precedingMove + "|faint|p2a: Gyarados")
        )
        assertEquals(
            BattleFeedPresentation.DEFAULT_MESSAGE_CYCLE_MILLIS,
            BattlePlaybackTiming.pauseAfter(listOf("|start"))
        )
        assertEquals(
            BattleFeedPresentation.DEFAULT_MESSAGE_CYCLE_MILLIS,
            BattlePlaybackTiming.pauseAfter(listOf("|switchout|p1a: Pikachu|U-turn"))
        )
    }

    @Test
    fun isolatesShowdownPreMajorAndConfusionDamageMessages() {
        val precedingMove = "|move|p1a: Pikachu|Thunderbolt|p2a: Gyarados"
        listOf(
            "|-damage|p1a: Pikachu|80/100|[from] confusion",
            "|-curestatus|p1a: Pikachu|par|[from] ability: Natural Cure",
            "|-start|p1a: Pikachu|typechange|Fire|[from] ability: Protean",
            "|-activate|p1a: Pikachu|confusion"
        ).forEach { transition ->
            assertEquals(
                listOf(listOf(precedingMove), listOf(transition)),
                BattlePlaybackTiming.chunks(listOf(precedingMove, transition))
            )
        }
    }

    @Test
    fun isolatesEachExplicitAnimationAndKeepsItsHitWithThatAnimation() {
        val lines = listOf(
            "|move|p1a: Cinderace|Triple Axel|p2a: Dragapult",
            "|-damage|p2a: Dragapult|80/100",
            "|-anim|p1a: Cinderace|Triple Axel|p2a: Dragapult",
            "|-damage|p2a: Dragapult|60/100",
            "|-anim|p1a: Cinderace|Triple Axel|p2a: Dragapult",
            "|-damage|p2a: Dragapult|40/100",
            "|-hitcount|p2a: Dragapult|3"
        )
        val chunks = BattlePlaybackTiming.chunks(lines)

        assertEquals(
            listOf(
                lines.take(2),
                lines.slice(2..3),
                lines.slice(4..6)
            ),
            chunks
        )
        assertEquals(
            BattleFeedPresentation.DEFAULT_MESSAGE_CYCLE_MILLIS,
            BattlePlaybackTiming.pauseAfter(chunks[1])
        )
    }

    @Test
    fun givesFaintsLongerReadingTimeThanOrdinaryMoves() {
        assertEquals(2_600L, BattlePlaybackTiming.pauseAfter(listOf("|move|p1a: Pikachu|Tackle|p2a: Eevee")))
        assertEquals(4_800L, BattlePlaybackTiming.pauseAfter(listOf("|move|p1a: Pikachu|Tackle|p2a: Eevee", "|faint|p2a: Eevee")))
    }

    @Test
    fun givesEveryGeneratedSwitchMessageTimeToBeRead() {
        val pause = BattlePlaybackTiming.pauseAfter(
            listOf("|switch|p1a: Raichu|Raichu, L50|100/100"),
            generatedMessageCount = 2
        )

        assertEquals(4_800L, pause)
        assertEquals(6_400L, BattlePlaybackTiming.scaledPause(pause, 0.75f))
    }

    @Test
    fun keepsLaterBattlePacketsBehindMessagesAlreadyQueuedForDisplay() {
        assertEquals(
            8_400L,
            BattlePlaybackTiming.pauseAfter(
                listOf("|switch|p2a: Perrserker|Perrserker, L80|100/100"),
                generatedMessageCount = 1,
                feedPlaybackBudgetMillis = 8_400L
            )
        )
    }

    @Test
    fun givesStandaloneAnimationPacketsHumanReadableTiming() {
        assertEquals(
            BattleSceneTiming.lightweightMoveDurationNanos / 1_000_000L,
            BattlePlaybackTiming.pauseAfter(listOf("|-anim|p1a: Pikachu|Thunderbolt|p2a: Eevee"))
        )
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
    fun isolatesEveryBattleEndEventAndLeavesTimeToReadIt() {
        val lines = listOf(
            "|move|p1a: Pikachu|Tackle|p2a: Eevee",
            "|draw|The battle ended in a draw.",
            "|prematureend|"
        )

        assertEquals(
            listOf(
                listOf("|move|p1a: Pikachu|Tackle|p2a: Eevee"),
                listOf("|draw|The battle ended in a draw."),
                listOf("|prematureend|")
            ),
            BattlePlaybackTiming.chunks(lines)
        )
        assertEquals(4_000L, BattlePlaybackTiming.pauseAfter(listOf("|draw|The battle ended in a draw.")))
        assertEquals(4_000L, BattlePlaybackTiming.pauseAfter(listOf("|prematureend|")))
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
    fun leavesDirectRoomMessagesOnScreenLongEnoughToRead() {
        assertEquals(
            7_200L,
            BattlePlaybackTiming.pauseAfter(
                listOf(
                    "A moderator paused the battle.",
                    "||The server will restart after this battle.",
                    "||Maintenance|[silent]"
                )
            )
        )
    }

    @Test
    fun scalesReplayPausesWithoutChangingLiveTiming() {
        assertEquals(1_300L, BattlePlaybackTiming.scaledPause(2_600L, 2f))
        assertEquals(5_200L, BattlePlaybackTiming.scaledPause(2_600L, 0.5f))
        assertEquals(2_600L, BattlePlaybackTiming.scaledPause(2_600L, 1f))
    }
}
