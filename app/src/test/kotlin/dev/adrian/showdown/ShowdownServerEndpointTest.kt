package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownServerEndpointTest {
    @Test
    fun turnsLocalHttpInputIntoSockJsWebSocketEndpoint() {
        val endpoint = ShowdownServerEndpoint.fromInput("http://10.0.2.2:8000")

        assertEquals("10.0.2.2:8000", endpoint?.displayName)
        assertEquals("ws://10.0.2.2:8000/showdown/websocket", endpoint?.webSocketUrl)
        assertEquals("http://10.0.2.2:8000/api/login", endpoint?.loginUrl)
        assertEquals("http://10.0.2.2:8000/api/register", endpoint?.registrationUrl)
    }

    @Test
    fun preservesAnExplicitWebSocketPath() {
        val endpoint = ShowdownServerEndpoint.fromInput("wss://example.test/showdown/websocket")

        assertEquals("wss://example.test/showdown/websocket", endpoint?.webSocketUrl)
        assertEquals("https://example.test/api/login", endpoint?.loginUrl)
    }

    @Test
    fun preservesAConfiguredServerPathForAuthenticationEndpoints() {
        val endpoint = ShowdownServerEndpoint.fromInput("http://example.test/ps/showdown/websocket")

        assertEquals("http://example.test/ps/api/login", endpoint?.loginUrl)
        assertEquals("http://example.test/ps/api/upkeep", endpoint?.upkeepUrl)
    }

    @Test
    fun usesTheOfficialLoginServerForOfficialSimulatorHosts() {
        val endpoint = ShowdownServerEndpoint.fromInput("wss://sim3.psim.us/showdown/websocket")

        assertEquals("https://play.pokemonshowdown.com/api/login", endpoint?.loginUrl)
        assertEquals("https://play.pokemonshowdown.com/api/register", endpoint?.registrationUrl)
        assertEquals("https://play.pokemonshowdown.com/api/changepassword", endpoint?.changePasswordUrl)
        assertEquals("https://play.pokemonshowdown.com/api/upkeep", endpoint?.upkeepUrl)
        assertEquals("https://pokemonshowdown.com/ladder/gen9ou.json", endpoint?.ladderUrl("gen9ou"))
    }

    @Test
    fun usesTheConfiguredCustomServerForCustomLogin() {
        val endpoint = ShowdownServerEndpoint.fromInput("http://10.0.2.2:8000")

        assertEquals("http://10.0.2.2:8000/api/login", endpoint?.loginUrl)
        assertEquals("http://10.0.2.2:8000/api/changepassword", endpoint?.changePasswordUrl)
        assertEquals("http://10.0.2.2:8000/api/upkeep", endpoint?.upkeepUrl)
    }

    @Test
    fun rejectsUnsupportedEndpointSchemes() {
        assertNull(ShowdownServerEndpoint.fromInput("ftp://example.test"))
    }

    @Test
    fun buildsAConfiguredLadderApiUrlForCustomServers() {
        val endpoint = ShowdownServerEndpoint.fromInput("http://example.test/ps/showdown/websocket")

        assertEquals("http://example.test/ps/ladder/gen9ou.json", endpoint?.ladderUrl("gen9ou"))
    }
}
