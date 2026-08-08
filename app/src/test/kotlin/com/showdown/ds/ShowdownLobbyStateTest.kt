package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownLobbyStateTest {
    @Test
    fun tracksSearchesAndBattleRoomsFromTheServer() {
        val lobby = ShowdownLobbyState()

        lobby.applyProtocol(listOf("|updatesearch|{\"searching\":[\"gen7randombattle\"],\"games\":{\"battle-gen7randombattle-1\":\"Adrian vs. Gladion\"}}"))

        assertTrue(lobby.isSearching("gen7randombattle"))
        assertEquals("Adrian vs. Gladion", lobby.battles["battle-gen7randombattle-1"])
    }

    @Test
    fun tracksIncomingAndOutgoingChallenges() {
        val lobby = ShowdownLobbyState()

        lobby.applyProtocol(listOf("|updatechallenges|{\"challengesFrom\":{\"gladion\":\"gen7randombattle\"},\"challengeTo\":{\"to\":\"lillie\",\"format\":\"gen7ou\"}}"))

        assertEquals("gen7randombattle", lobby.incomingChallenges["gladion"])
        assertEquals(ShowdownLobbyState.OutgoingChallenge("lillie", "gen7ou"), lobby.outgoingChallenge)
    }

    @Test
    fun createsPackedTeamCommandsForBuiltTeamFormats() {
        assertEquals(listOf("/utm Pikachu||lightball", "/search gen7ou"), ShowdownLobbyState.searchCommands("gen7ou", "Pikachu||lightball"))
        assertEquals(listOf("/utm null", "/search gen7randombattle"), ShowdownLobbyState.searchCommands("gen7randombattle", null))
        assertEquals(
            listOf("/utm Pikachu||lightball", "/challenge Gladion, gen7ou"),
            ShowdownLobbyState.challengeCommands("Gladion", "gen7ou", "Pikachu||lightball")
        )
        assertEquals(listOf("/utm null", "/accept Gladion"), ShowdownLobbyState.acceptChallengeCommands("Gladion", null))
        assertEquals("/reject Gladion", ShowdownLobbyState.rejectChallengeCommand("Gladion"))
        assertEquals("/cancelchallenge Gladion", ShowdownLobbyState.cancelChallengeCommand("Gladion"))
        assertEquals("/cancelsearch", ShowdownLobbyState.cancelSearchCommand())
        assertEquals("/join battle-gen7ou-1", ShowdownLobbyState.joinBattleCommand(" battle-gen7ou-1 "))
    }

    @Test
    fun clearsSearchesWhenTheServerSendsAnEmptyState() {
        val lobby = ShowdownLobbyState()
        lobby.applyProtocol(listOf("|updatesearch|{\"searching\":[\"gen7randombattle\"]}"))
        lobby.applyProtocol(listOf("|updatesearch|{}"))

        assertFalse(lobby.isSearching("gen7randombattle"))
    }

    @Test
    fun clearsOnlyTheCancelledSearchLocally() {
        val lobby = ShowdownLobbyState()
        lobby.applyProtocol(listOf("|updatesearch|{\"searching\":[\"gen7ou\",\"gen7randombattle\"]}"))

        lobby.clearSearch("gen7ou")

        assertFalse(lobby.isSearching("gen7ou"))
        assertTrue(lobby.isSearching("gen7randombattle"))
    }
}
