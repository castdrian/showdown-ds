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
    fun tracksPublicRoomsFromTheShowdownRoomQuery() {
        val lobby = ShowdownLobbyState()

        lobby.applyProtocol(
            listOf(
                "|queryresponse|rooms|{" +
                    "\"userCount\":123," +
                    "\"official\":[{\"title\":\"Lobby\",\"userCount\":42}]," +
                    "\"chat\":[{\"title\":\"OverUsed\",\"desc\":\"Smogon discussion\",\"userCount\":18}]" +
                    "}"
            )
        )

        assertEquals(listOf("lobby", "overused"), lobby.rooms.map(ShowdownLobbyState.RoomSummary::id))
        assertEquals("Official", lobby.rooms.first().section)
        assertEquals(18, lobby.rooms[1].userCount)
    }

    @Test
    fun tracksActiveBattleRoomsFromTheShowdownRoomQuery() {
        val lobby = ShowdownLobbyState()

        lobby.applyProtocol(
            listOf(
                "|queryresponse|roomlist|{" +
                    "\"rooms\":{" +
                        "\"battle-gen9ou-1\":{" +
                            "\"p1\":\"Alice\",\"p2\":\"Bob\",\"minElo\":1500" +
                        "}" +
                    "}" +
                    "}"
            )
        )

        assertEquals(1, lobby.battleRooms.size)
        assertEquals("battle-gen9ou-1", lobby.battleRooms.single().id)
        assertEquals("Alice", lobby.battleRooms.single().playerOne)
        assertEquals("1500", lobby.battleRooms.single().minimumElo)
    }

    @Test
    fun tracksLadderRowsFromTheShowdownQuery() {
        val lobby = ShowdownLobbyState()

        lobby.applyProtocol(
            listOf(
                "|queryresponse|laddertop|{" +
                    "\"formatid\":\"gen9ou\"," +
                    "\"toplist\":[{" +
                        "\"username\":\"Alice\",\"elo\":1675.4,\"gxe\":82.1,\"rpr\":1700,\"rprd\":55,\"coil\":1200" +
                    "}]" +
                    "}"
            )
        )

        assertEquals(1, lobby.ladder.size)
        assertEquals("Alice", lobby.ladder.single().username)
        assertEquals(1675.4, lobby.ladder.single().elo, 0.01)
        assertEquals(1200.0, lobby.ladder.single().coil ?: 0.0, 0.01)
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

    @Test
    fun removesACompletedBattleRoomFromTheLobby() {
        val lobby = ShowdownLobbyState()
        lobby.applyProtocol(listOf("|updatesearch|{\"games\":{\"battle-gen9ou-1\":\"Adrian vs. Gladion\"}}"))

        lobby.clearBattle("battle-gen9ou-1")

        assertTrue(lobby.battles.isEmpty())
    }

    @Test
    fun extractsTheHumanReadableNoInitReason() {
        assertEquals(
            "The room \"battle-gen9ou-1\" does not exist.",
            ShowdownLobbyState.noInitReason(listOf("|noinit|nonexistent|The room \"battle-gen9ou-1\" does not exist."))
        )
        assertEquals(null, ShowdownLobbyState.noInitReason(listOf("|init|battle")))
    }

    @Test
    fun clearsCachedRoomsAndChallengesWhenTheSessionEnds() {
        val lobby = ShowdownLobbyState()
        lobby.applyProtocol(
            listOf(
                "|updatesearch|{\"searching\":[\"gen9ou\"],\"games\":{\"battle-gen9ou-1\":\"Adrian vs. Gladion\"}}",
                "|updatechallenges|{\"challengesFrom\":{\"gladion\":\"gen9ou\"},\"challengeTo\":{\"to\":\"misty\",\"format\":\"gen9ou\"}}"
            )
        )

        lobby.clear()

        assertTrue(lobby.battles.isEmpty())
        assertTrue(lobby.incomingChallenges.isEmpty())
        assertEquals(null, lobby.outgoingChallenge)
        assertTrue(lobby.rooms.isEmpty())
        assertTrue(lobby.battleRooms.isEmpty())
        assertFalse(lobby.isSearching("gen9ou"))
    }

    @Test
    fun findsOnlyBattleRoomsThatAppearedSinceTheLastLobbyUpdate() {
        val lobby = ShowdownLobbyState()
        lobby.applyProtocol(listOf("|updatesearch|{\"games\":{\"battle-gen9ou-1\":\"Adrian vs. Gladion\"}}"))

        lobby.applyProtocol(listOf("|updatesearch|{\"games\":{\"battle-gen9ou-1\":\"Adrian vs. Gladion\",\"battle-gen9ou-2\":\"Misty vs. Gary\"}}"))

        assertEquals("battle-gen9ou-2", lobby.firstNewBattle(setOf("battle-gen9ou-1")))
    }
}
