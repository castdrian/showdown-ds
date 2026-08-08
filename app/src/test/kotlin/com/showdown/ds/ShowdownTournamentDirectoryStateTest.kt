package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTournamentDirectoryStateTest {
    @Test
    fun parsesInstantTournamentLinksFromDirectoryPage() {
        val state = ShowdownTournamentDirectoryState()
        state.applyProtocol(
            "view-tournaments-all",
            listOf(
                "|title|[Tournaments] All",
                "|pagehtml|<h2>Instant Tournaments</h2><strong>Accepting Signups:</strong><ul><li><a href=\"/gen9ou-123\" class=\"blocklink\">&laquo;<strong>gen9ou-123</strong>&raquo;<small>(4 players)</small><br /><small>OU Single Elimination</small></a></li></ul>"
            )
        )

        assertEquals("[Tournaments] All", state.snapshot.title)
        assertEquals(1, state.snapshot.tournaments.size)
        assertEquals("gen9ou-123", state.snapshot.tournaments.single().roomId)
        assertEquals("gen9ou-123", state.snapshot.tournaments.single().roomName)
        assertEquals(4, state.snapshot.tournaments.single().playerCount)
        assertTrue(state.snapshot.text.contains("Instant Tournaments"))
    }

    @Test
    fun buildsDirectoryCommands() {
        assertEquals("/join view-tournaments-all", ShowdownTournamentDirectoryState.pageCommand())
        assertEquals("/join battle-gen9ou-1", ShowdownTournamentDirectoryState.joinCommand(" battle-gen9ou-1 "))
    }
}
