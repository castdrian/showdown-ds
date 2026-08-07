package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownServerEndpointTest {
    @Test
    fun turnsLocalHttpInputIntoSockJsWebSocketEndpoint() {
        val endpoint = ShowdownServerEndpoint.fromInput("http://10.0.2.2:8000")

        assertEquals("10.0.2.2:8000", endpoint?.displayName)
        assertEquals("ws://10.0.2.2:8000/showdown/websocket", endpoint?.webSocketUrl)
    }

    @Test
    fun preservesAnExplicitWebSocketPath() {
        val endpoint = ShowdownServerEndpoint.fromInput("wss://example.test/showdown/websocket")

        assertEquals("wss://example.test/showdown/websocket", endpoint?.webSocketUrl)
    }

    @Test
    fun rejectsUnsupportedEndpointSchemes() {
        assertNull(ShowdownServerEndpoint.fromInput("ftp://example.test"))
    }
}
