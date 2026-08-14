package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTournamentStateTest {
    @Test
    fun tracksTournamentUpdatesAndBracketMatches() {
        val state = ShowdownTournamentState()

        state.applyProtocol("|tournament|create|gen9ou|Single Elimination|8")
        state.applyProtocol(
            "|tournament|update|{" +
                "\"isStarted\":true," +
                "\"isJoined\":true," +
                "\"challenges\":[\"Misty\"]," +
                "\"challengeBys\":[\"Gary\"]," +
                "\"bracketData\":{" +
                    "\"type\":\"tree\",\"rootNode\":{" +
                        "\"state\":\"available\",\"children\":[{" +
                            "\"team\":\"Adrian\"},{\"team\":\"Misty\"}]" +
                    "}" +
                "}}"
        )

        assertEquals("gen9ou", state.snapshot.format)
        assertTrue(state.snapshot.isStarted)
        assertTrue(state.snapshot.isJoined)
        assertEquals(listOf("Misty"), state.snapshot.challenges)
        assertEquals(listOf("Adrian vs Misty · available"), state.bracketLines())
        assertEquals("In progress", state.status())
    }

    @Test
    fun preservesPartialUpdatesAndRecordsTournamentEvents() {
        val state = ShowdownTournamentState()
        state.applyProtocol("|tournament|create|gen9ou|Round Robin|0|Spring Cup")
        state.applyProtocol("|tournament|update|{\"isJoined\":true,\"challenged\":\"Misty\"}")
        state.applyProtocol("|tournament|battlestart|Adrian|Misty|battle-gen9ou-1")
        state.applyProtocol("|tournament|battleend|Adrian|Misty|win|3,0|success|battle-gen9ou-1")

        assertEquals("Spring Cup", state.snapshot.format)
        assertEquals("gen9ou", state.snapshot.teambuilderFormat)
        assertTrue(state.snapshot.isJoined)
        assertEquals("Misty", state.snapshot.challenged)
        assertTrue(state.snapshot.events.any { it.contains("Tournament battle") })
        assertTrue(state.snapshot.events.any { it.contains("Adrian won") })
    }

    @Test
    fun handlesTournamentEndAndCommands() {
        val state = ShowdownTournamentState()
        state.applyProtocol("|tournament|create|gen9ou|Single Elimination|4")
        state.applyProtocol("|tournament|end|{\"results\":[[\"Adrian\"]],\"format\":\"gen9ou\",\"generator\":\"Single Elimination\"}")

        assertFalse(state.snapshot.isActive)
        assertEquals("Finished", state.status())
        assertTrue(state.snapshot.events.last().contains("Adrian"))
        assertEquals("/tournament join", ShowdownTournamentState.joinCommand())
        assertEquals("/tournament challenge Misty", ShowdownTournamentState.challengeCommand(" Misty "))
        assertEquals("/tournament acceptchallenge", ShowdownTournamentState.acceptChallengeCommand())
        assertEquals("/tournament cancelchallenge", ShowdownTournamentState.cancelChallengeCommand())
        assertEquals("/tournament vtm", ShowdownTournamentState.validateTeamCommand())
    }

    @Test
    fun formatsTournamentTitleThroughTheKnownFormatLabeler() {
        val state = ShowdownTournamentState()
        state.applyProtocol("|tournament|create|gen9ou|Single Elimination|4")

        assertEquals("[Gen 9] OU Tournament", state.title { format -> ShowdownTeamLibraryQuery.displayFormat(format) })
    }
}
