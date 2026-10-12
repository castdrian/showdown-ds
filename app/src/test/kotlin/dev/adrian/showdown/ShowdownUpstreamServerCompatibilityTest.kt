package dev.adrian.showdown

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShowdownUpstreamServerCompatibilityTest {
    @Test
    fun connectsToUpstreamServerAndJoinsLobby() {
        val webSocketUrl = System.getenv("SHOWDOWN_TEST_SERVER_URL")?.takeIf(String::isNotBlank)
        assumeTrue("Upstream Showdown server URL was not configured", webSocketUrl != null)

        val connected = CountDownLatch(1)
        val receivedChallenge = CountDownLatch(1)
        val renamedGuest = CountDownLatch(1)
        val joinedLobby = CountDownLatch(1)
        val protocolMessages = CopyOnWriteArrayList<Pair<String?, List<String>>>()
        val listener = object : ShowdownConnection.Listener {
            override fun onConnectionStateChanged(state: ShowdownConnection.State, detail: String) {
                if (state == ShowdownConnection.State.CONNECTED) connected.countDown()
            }

            override fun onProtocol(roomId: String?, lines: List<String>) {
                protocolMessages += roomId to lines
                if (lines.any { it.startsWith("|challstr|") }) receivedChallenge.countDown()
                if (lines.any { it.startsWith("|updateuser| PSClient|1|") }) renamedGuest.countDown()
                if (roomId == null && lines.any { it == "|init|chat" }) joinedLobby.countDown()
            }
        }
        val endpoint = ShowdownServerEndpoint.fromInput(webSocketUrl!!)
            ?: error("Configured Showdown server URL is invalid")
        val connection = ShowdownConnection(endpoint, listener)

        try {
            connection.connect()

            assertTrue("The upstream server did not open the WebSocket transport", connected.await(20, TimeUnit.SECONDS))
            assertTrue("The upstream server did not send its login challenge", receivedChallenge.await(20, TimeUnit.SECONDS))
            assertTrue("The client rejected its guest rename", connection.sendGlobal("/trn PSClient,0,"))
            assertTrue(
                "The upstream server did not accept the guest rename",
                renamedGuest.await(20, TimeUnit.SECONDS)
            )
            assertTrue("The client rejected the lobby command", connection.sendGlobal("/join lobby"))
            assertTrue(
                "The upstream server did not initialize the lobby",
                joinedLobby.await(20, TimeUnit.SECONDS)
            )
            assertTrue(
                "The upstream server did not return the lobby title",
                protocolMessages.any { (roomId, lines) -> roomId == null && lines.any { it == "|title|Lobby" } }
            )
        } finally {
            connection.close()
        }
    }
}
