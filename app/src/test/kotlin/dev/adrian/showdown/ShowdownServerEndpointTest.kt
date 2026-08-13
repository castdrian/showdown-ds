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
    fun usesTheOfficialLoginServerForOfficialSimulatorHosts() {
        val endpoint = ShowdownServerEndpoint.fromInput("wss://sim3.psim.us/showdown/websocket")

        assertEquals("https://play.pokemonshowdown.com/api/login", endpoint?.loginUrl)
        assertEquals("https://play.pokemonshowdown.com/api/register", endpoint?.registrationUrl)
        assertEquals("https://play.pokemonshowdown.com/api/changepassword", endpoint?.changePasswordUrl)
    }

    @Test
    fun usesTheConfiguredCustomServerForCustomLogin() {
        val endpoint = ShowdownServerEndpoint.fromInput("http://10.0.2.2:8000")

        assertEquals("http://10.0.2.2:8000/api/login", endpoint?.loginUrl)
        assertEquals("http://10.0.2.2:8000/api/changepassword", endpoint?.changePasswordUrl)
    }

    @Test
    fun rejectsUnsupportedEndpointSchemes() {
        assertNull(ShowdownServerEndpoint.fromInput("ftp://example.test"))
    }
}
