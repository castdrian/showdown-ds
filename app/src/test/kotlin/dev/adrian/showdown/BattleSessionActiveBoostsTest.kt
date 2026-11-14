package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleSessionActiveBoostsTest {
    private fun singlesBattle(vararg afterStart: String): BattleSession = BattleSession().apply {
        setLocalUsername("PLAYER")
        applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|PLAYER||",
                "|player|p2|OPPONENT||",
                "|gametype|singles",
                "|start",
                "|switch|p1a: Tornadus|Tornadus, L79, M|100/100",
                "|switch|p2a: Ditto|Ditto, L87|100/100"
            ) + afterStart
        )
    }

    @Test
    fun BattleSession_ActiveBoostsForYourBoostedPokemon_ReturnsItsStages() {
        val session = singlesBattle("|-boost|p1a: Tornadus|atk|2")

        val boosts = session.activeBoostsFor(player = true, pokemon = "Tornadus")

        assertEquals(mapOf("atk" to 2), boosts)
    }

    @Test
    fun BattleSession_ActiveBoostsForTheOpponentsLoweredPokemon_ReturnsNegativeStages() {
        val session = singlesBattle("|-unboost|p2a: Ditto|spe|1")

        val boosts = session.activeBoostsFor(player = false, pokemon = "Ditto")

        assertEquals(mapOf("spe" to -1), boosts)
    }

    @Test
    fun BattleSession_ActiveBoostsForAPokemonOnTheBench_ReturnsNothing() {
        val session = singlesBattle("|-boost|p1a: Tornadus|atk|2")

        val boosts = session.activeBoostsFor(player = true, pokemon = "Trevenant")

        assertEquals(emptyMap<String, Int>(), boosts)
    }

    @Test
    fun BattleSession_ActiveBoostsAfterTheBoostsAreCleared_ReturnsNothing() {
        val session = singlesBattle("|-boost|p1a: Tornadus|atk|2", "|-clearallboost")

        val boosts = session.activeBoostsFor(player = true, pokemon = "Tornadus")

        assertEquals(emptyMap<String, Int>(), boosts)
    }

    @Test
    fun BattleSession_ActiveBoostsAreSeparatePerSide() {
        val session = singlesBattle("|-boost|p1a: Tornadus|atk|2")

        val opponentBoosts = session.activeBoostsFor(player = false, pokemon = "Ditto")

        assertEquals(emptyMap<String, Int>(), opponentBoosts)
    }
}
