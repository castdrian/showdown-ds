package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownDefaultEndpointTest {
    @Test
    fun ShowdownServerEndpoint_DefaultForLocalBuild_ReturnsEmulatorHostServer() {
        val useLocalServer = true

        val endpoint = ShowdownServerEndpoint.defaultFor(useLocalServer)

        assertEquals(ShowdownServerEndpoint.emulatorLocal, endpoint)
    }

    @Test
    fun ShowdownServerEndpoint_DefaultForNonLocalBuild_ReturnsOfficialServer() {
        val useLocalServer = false

        val endpoint = ShowdownServerEndpoint.defaultFor(useLocalServer)

        assertEquals(ShowdownServerEndpoint.playShowdown, endpoint)
    }

    @Test
    fun ShowdownServerEndpoint_LocalEmulatorEndpoint_TargetsHostLoopbackOverCleartext() {
        val endpoint = ShowdownServerEndpoint.emulatorLocal

        assertEquals("ws://10.0.2.2:8000/showdown/websocket", endpoint.webSocketUrl)
    }
}
