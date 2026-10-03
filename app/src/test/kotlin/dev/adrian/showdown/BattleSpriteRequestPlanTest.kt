package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSpriteRequestPlanTest {
    @Test
    fun faintReplacementProducesANewVisibleSpriteRequest() {
        val session = BattleSession().apply {
            setLocalUsername("ADRIAN")
            applyProtocolPacket(
                listOf(
                    "|player|p1|ADRIAN||",
                    "|player|p2|OPPONENT||",
                    "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                    "|switch|p2a: Charizard|Charizard, L50|100/100"
                )
            )
        }
        val beforeFaint = visibleSpriteRequestPlan(session)
        val tracker = BattleSpriteRequestTracker()

        assertTrue(tracker.updateIfChanged(beforeFaint))
        assertFalse(tracker.updateIfChanged(beforeFaint))

        session.applyProtocolPacket(
            listOf(
                "|-damage|p2a: Charizard|0 fnt",
                "|faint|p2a: Charizard",
                "|switch|p2a: Blastoise|Blastoise, L50|100/100"
            )
        )

        val afterReplacement = visibleSpriteRequestPlan(session)
        val latestMessage = session.battleFeedMessages().last()

        assertNotEquals(beforeFaint, afterReplacement)
        assertEquals("Charizard", beforeFaint.opponentLead?.species)
        assertEquals("Blastoise", afterReplacement.opponentLead?.species)
        assertNull(session.switchOutVisualForBattleFeed(latestMessage.id))
        assertTrue(tracker.updateIfChanged(afterReplacement))
    }

    @Test
    fun faintReplacementRefreshesOnlyItsDoubleBattleSlot() {
        val session = BattleSession().apply {
            setLocalUsername("ADRIAN")
            applyProtocolPacket(
                listOf(
                    "|player|p1|ADRIAN||",
                    "|player|p2|OPPONENT||",
                    "|gametype|doubles",
                    "|switch|p1a: Gholdengo|Gholdengo, L50|100/100",
                    "|switch|p1b: Sableye|Sableye, L50|100/100",
                    "|switch|p2a: Charizard|Charizard, L50|100/100",
                    "|switch|p2b: Gengar|Gengar, L50|100/100"
                )
            )
        }
        val beforeFaint = visibleSpriteRequestPlan(session)
        val tracker = BattleSpriteRequestTracker()

        assertTrue(tracker.updateIfChanged(beforeFaint))
        session.applyProtocolPacket(
            listOf(
                "|-damage|p2a: Charizard|0 fnt",
                "|faint|p2a: Charizard",
                "|switch|p2a: Blastoise|Blastoise, L50|100/100"
            )
        )

        val afterReplacement = visibleSpriteRequestPlan(session)
        val beforeSlots = beforeFaint.opponentActive.associate { it.slot to it.request.species }
        val afterSlots = afterReplacement.opponentActive.associate { it.slot to it.request.species }

        assertTrue(tracker.updateIfChanged(afterReplacement))
        assertEquals(mapOf("p2a" to "Charizard", "p2b" to "Gengar"), beforeSlots)
        assertEquals(mapOf("p2a" to "Blastoise", "p2b" to "Gengar"), afterSlots)
    }

    private fun visibleSpriteRequestPlan(session: BattleSession) = BattleSpriteRequests.forScene(
        playerCombatants = session.playerActiveCombatants(),
        opponentCombatants = session.opponentActiveCombatants(),
        singlesBattle = session.isSinglesBattle(),
        style = session.spriteStyle,
        playerFallbackSpecies = session.playerPokemon,
        opponentFallbackSpecies = session.opponentPokemon
    )
}
