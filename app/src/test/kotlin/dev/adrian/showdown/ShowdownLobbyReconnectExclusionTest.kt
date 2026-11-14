package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownLobbyReconnectExclusionTest {
    private val heldRoom = "battle-gen9ou-1"
    private val otherRoom = "battle-gen9ou-2"

    private fun lobbyReporting(vararg rooms: String): ShowdownLobbyState = ShowdownLobbyState().apply {
        val games = rooms.joinToString(",") { "\"$it\":\"A vs. B\"" }
        applyProtocol(listOf("|updatesearch|{\"games\":{$games}}"))
    }

    @Test
    fun ShowdownLobbyState_BattleForReconnectWithOnlyAHeldRoom_ReturnsNothing() {
        val lobby = lobbyReporting(heldRoom)

        val room = lobby.battleForReconnect(null, null, excludedRoomIds = setOf(heldRoom))

        assertEquals(null, room)
    }

    @Test
    fun ShowdownLobbyState_BattleForReconnectWithHeldAndOtherRoom_ReturnsTheOtherRoom() {
        val lobby = lobbyReporting(heldRoom, otherRoom)

        val room = lobby.battleForReconnect(null, null, excludedRoomIds = setOf(heldRoom))

        assertEquals(otherRoom, room)
    }

    @Test
    fun ShowdownLobbyState_BattleForReconnectWithoutExclusions_KeepsReturningTheReportedRoom() {
        val lobby = lobbyReporting(heldRoom)

        val room = lobby.battleForReconnect(null, null)

        assertEquals(heldRoom, room)
    }
}
