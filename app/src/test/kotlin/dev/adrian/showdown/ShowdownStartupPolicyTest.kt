package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownStartupPolicyTest {
    @Test
    fun freshLaunchConnectsToLobby() {
        assertTrue(ShowdownStartupPolicy.shouldConnectToLobby(false, false, null))
    }

    @Test
    fun restoredConnectionDoesNotStartDuplicateSocket() {
        assertFalse(ShowdownStartupPolicy.shouldConnectToLobby(true, false, null))
    }

    @Test
    fun replayDeepLinkDoesNotStartLobbySocket() {
        assertFalse(ShowdownStartupPolicy.shouldConnectToLobby(false, true, null))
    }

    @Test
    fun restoredReplayDoesNotStartLobbySocket() {
        assertFalse(ShowdownStartupPolicy.shouldConnectToLobby(false, false, "https://replay.pokemonshowdown.com/gen9ou-1.json"))
    }
}
