package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleFeedSceneStateTest {
    @Test
    fun replayRequiresAnActiveCombatantInsteadOfStaleFallbackDetails() {
        val session = BattleSession().apply {
            prepareForReplay()
            setReplayMode(true)
            setLiveBattleActive(true)
        }

        assertTrue(BattleFeedSceneState.hasKnownPokemon(null, session.playerDetails()))
        assertFalse(
            BattleFeedSceneState.hasKnownPokemon(
                null,
                session.playerDetails(),
                requireActiveCombatant = true
            )
        )

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|Alice||",
                "|player|p2|Bob||",
                "|gametype|singles",
                "|switch|p1a: Salamence|Salamence, L50|100/100"
            )
        )

        assertFalse(
            BattleFeedSceneState.hasKnownPokemon(
                session.opponentActiveCombatants().firstOrNull(),
                session.opponentDetails(),
                requireActiveCombatant = true
            )
        )
    }

    @Test
    fun replayDoesNotShowAnUnknownStatusCardBeforeThatSideSendsOutAPokemon() {
        val session = BattleSession().apply {
            setLocalUsername("Alice")
            setReplayMode(true)
            applyProtocolPacket(
                listOf(
                    "|init|battle",
                    "|player|p1|Alice||",
                    "|player|p2|Bob||",
                    "|gametype|singles",
                    "|switch|p1a: Salamence|Salamence, L50|100/100"
                )
            )
        }

        assertFalse(
            BattleFeedSceneState.hasKnownPokemon(
                session.opponentActiveCombatants().firstOrNull(),
                session.opponentDetails(),
                requireActiveCombatant = true
            )
        )

        session.applyProtocolPacket(listOf("|switch|p2a: Hypno|Hypno, L50|100/100"))

        assertTrue(
            BattleFeedSceneState.hasKnownPokemon(
                session.opponentActiveCombatants().firstOrNull(),
                session.opponentDetails(),
                requireActiveCombatant = true
            )
        )
    }
}
