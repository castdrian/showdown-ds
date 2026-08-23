package dev.adrian.showdown

import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.Closeable
import java.io.InputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.ServerSocket
import java.net.Socket
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import okhttp3.OkHttpClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownConnectionLifecycleTest {
    @Test
    fun dispatchesRawRoomsAndSendsGlobalCommandsAfterReadiness() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient
        )
        try {
            connection.connect()
            val socket = server.awaitClient()
            server.sendText(socket, ">lobby\n|updateuser| Guest 1|0|1")

            assertTrue(listener.connected.await(2, TimeUnit.SECONDS))
            assertTrue(listener.protocol.await(2, TimeUnit.SECONDS))
            assertEquals(listOf("lobby" to listOf("|updateuser| Guest 1|0|1")), listener.protocolPackets)
            assertTrue(connection.sendGlobal("/search gen7randombattle"))
            assertEquals("|/search gen7randombattle", server.readClientText(socket))
        } finally {
            connection.close()
            server.close()
        }
    }

    @Test
    fun queuesCommandsUntilSockJsTransportIsReady() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient
        )
        try {
            connection.connect()
            val socket = server.awaitClient()

            assertTrue(connection.sendGlobal("/search gen9randombattle"))
            assertTrue(connection.sendGlobal("/cancelsearch"))
            server.sendText(socket, "o")

            assertTrue(listener.connected.await(2, TimeUnit.SECONDS))
            assertEquals("[\"|/search gen9randombattle\"]", server.readClientText(socket))
            assertEquals("[\"|/cancelsearch\"]", server.readClientText(socket))
        } finally {
            connection.close()
            server.close()
        }
    }

    @Test
    fun queuesCommandsUntilNativeTransportIsReady() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient
        )
        try {
            connection.connect()
            val socket = server.awaitClient()

            assertTrue(connection.sendGlobal("/search gen9randombattle"))
            assertTrue(connection.sendGlobal("/cancelsearch"))
            server.sendText(socket, ">lobby\n|updateuser| Guest 1|0|1")

            assertTrue(listener.connected.await(2, TimeUnit.SECONDS))
            assertEquals("|/search gen9randombattle", server.readClientText(socket))
            assertEquals("|/cancelsearch", server.readClientText(socket))
        } finally {
            connection.close()
            server.close()
        }
    }

    @Test
    fun discardingAConnectionAlsoDiscardsCommandsQueuedForItsSocket() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient
        )
        try {
            connection.connect()
            val socket = server.awaitClient()
            assertTrue(connection.sendGlobal("/search stale"))

            connection.disconnect()
            connection.connect()
            val replacement = server.awaitClient()
            server.sendText(replacement, "o")

            assertTrue(listener.connected.await(2, TimeUnit.SECONDS))
            runCatching { server.sendText(socket, ">lobby\\n|old|message") }
            Thread.sleep(100)
            assertEquals(1, listener.states.count { it.first == ShowdownConnection.State.CONNECTED })
            assertTrue(listener.protocolPackets.isEmpty())
            assertEquals(0, server.availableClientText(replacement))
        } finally {
            connection.close()
            server.close()
        }
    }

    @Test
    fun dispatchesSockJsMessagesAndEncodesRoomCommands() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient
        )
        try {
            connection.connect()
            val socket = server.awaitClient()
            server.sendText(socket, "o")
            assertTrue(listener.connected.await(2, TimeUnit.SECONDS))

            server.sendText(socket, "a[\">battle-gen9ou-1\\n|turn|1\"]")
            assertTrue(listener.protocol.await(2, TimeUnit.SECONDS))
            assertEquals(
                listOf("battle-gen9ou-1" to listOf("|turn|1")),
                listener.protocolPackets
            )
            assertTrue(connection.send("battle-gen9ou-1", "/choose move 1"))
            assertEquals("[\"battle-gen9ou-1|/choose move 1\"]", server.readClientText(socket))

            server.sendText(socket, "c[3000,\"server maintenance\"]")
            assertTrue(listener.disconnected.await(2, TimeUnit.SECONDS))
            assertEquals("server maintenance", listener.states.last().second)
            server.sendText(socket, "a[\">lobby\\n|late|message\"]")
            Thread.sleep(100)
            assertEquals(1, listener.protocolPackets.size)
            assertFalse(connection.sendGlobal("/search gen9ou"))
        } finally {
            connection.close()
            server.close()
        }
    }

    @Test
    fun ignoresFramesFromAReplacedSocket() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient
        )
        try {
            connection.connect()
            val firstSocket = server.awaitClient()
            server.sendText(firstSocket, "o")
            assertTrue(listener.connected.await(2, TimeUnit.SECONDS))

            connection.connect()
            val secondSocket = server.awaitClient()
            server.sendText(secondSocket, ">lobby\n|new|message")
            assertTrue(listener.protocol.await(2, TimeUnit.SECONDS))
            server.sendText(firstSocket, ">lobby\n|old|message")
            Thread.sleep(100)

            assertEquals(listOf("|new|message"), listener.protocolPackets.single().second)
            assertFalse(listener.protocolPackets.any { packet -> packet.second.contains("|old|message") })
        } finally {
            connection.close()
            server.close()
        }
    }

    @Test
    fun ignoresLateFramesAfterTransportFailure() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient
        )
        try {
            connection.connect()
            val socket = server.awaitClient()
            server.abort(socket)

            assertTrue(listener.failed.await(2, TimeUnit.SECONDS))
            runCatching { server.sendText(socket, ">lobby\n|late|message") }
            Thread.sleep(100)

            assertTrue(listener.protocolPackets.isEmpty())
            assertFalse(connection.sendGlobal("/search gen7randombattle"))
        } finally {
            connection.close()
            server.close()
        }
    }

    @Test
    fun failsWhenTransportNeverBecomesReady() {
        val server = LoopbackWebSocketServer()
        val listener = RecordingListener()
        val httpClient = testHttpClient()
        val connection = ShowdownConnection(
            ShowdownServerEndpoint("Loopback", "ws://127.0.0.1:${server.port}/showdown/websocket"),
            listener,
            httpClient,
            transportReadyTimeoutMillis = 100
        )
        try {
            connection.connect()
            server.awaitClient()

            assertTrue(listener.failed.await(2, TimeUnit.SECONDS))
            assertEquals("Showdown transport did not become ready in time.", listener.states.last().second)
            assertFalse(connection.isTransportReady())
            assertFalse(connection.sendGlobal("/search gen9randombattle"))
        } finally {
            connection.close()
            server.close()
        }
    }

    private fun testHttpClient() = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .writeTimeout(2, TimeUnit.SECONDS)
        .connectTimeout(2, TimeUnit.SECONDS)
        .build()

    private class RecordingListener : ShowdownConnection.Listener {
        val states = CopyOnWriteArrayList<Pair<ShowdownConnection.State, String>>()
        val protocolPackets = CopyOnWriteArrayList<Pair<String?, List<String>>>()
        val connected = java.util.concurrent.CountDownLatch(1)
        val disconnected = java.util.concurrent.CountDownLatch(1)
        val failed = java.util.concurrent.CountDownLatch(1)
        val protocol = java.util.concurrent.CountDownLatch(1)

        override fun onConnectionStateChanged(state: ShowdownConnection.State, detail: String) {
            states += state to detail
            when (state) {
                ShowdownConnection.State.CONNECTED -> connected.countDown()
                ShowdownConnection.State.DISCONNECTED -> disconnected.countDown()
                ShowdownConnection.State.FAILED -> failed.countDown()
                else -> Unit
            }
        }

        override fun onProtocol(roomId: String?, lines: List<String>) {
            protocolPackets += roomId to lines
            protocol.countDown()
        }
    }

    private class LoopbackWebSocketServer : Closeable {
        private val serverSocket = ServerSocket(0)
        private val clients = LinkedBlockingQueue<Socket>()
        private val closed = AtomicBoolean(false)
        private val acceptThread = thread(start = true, name = "showdown-test-websocket") {
            while (!closed.get()) {
                runCatching { serverSocket.accept() }.getOrNull()?.let { socket ->
                    if (handshake(socket)) clients += socket else socket.close()
                }
            }
        }

        val port: Int
            get() = serverSocket.localPort

        fun awaitClient(): Socket = clients.poll(2, TimeUnit.SECONDS)
            ?: error("The WebSocket client did not connect")

        fun sendText(socket: Socket, text: String) {
            val bytes = text.toByteArray(Charsets.UTF_8)
            val output = socket.getOutputStream()
            output.write(0x81)
            when {
                bytes.size < 126 -> output.write(bytes.size)
                bytes.size <= 0xffff -> {
                    output.write(126)
                    output.write(bytes.size shr 8)
                    output.write(bytes.size and 0xff)
                }
                else -> error("Test payload is too large")
            }
            output.write(bytes)
            output.flush()
        }

        fun abort(socket: Socket) {
            socket.close()
        }

        fun readClientText(socket: Socket): String {
            val input = socket.getInputStream()
            val first = input.read()
            val second = input.read()
            assertTrue(first >= 0 && second >= 0)
            assertEquals(0x1, first and 0x0f)
            val masked = second and 0x80 != 0
            var length = second and 0x7f
            if (length == 126) length = input.read() shl 8 or input.read()
            if (length == 127) error("Test payload is too large")
            val mask = if (masked) ByteArray(4).also { readFully(input, it) } else ByteArray(0)
            val payload = ByteArray(length).also { readFully(input, it) }
            if (masked) payload.indices.forEach { index -> payload[index] = (payload[index].toInt() xor mask[index % 4].toInt()).toByte() }
            return payload.toString(Charsets.UTF_8)
        }

        fun availableClientText(socket: Socket): Int = socket.getInputStream().available()

        private fun readFully(input: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) error("Unexpected end of WebSocket frame")
                offset += count
            }
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            serverSocket.close()
            clients.toList().forEach { runCatching { it.close() } }
            runCatching { acceptThread.join(500) }
        }

        private fun handshake(socket: Socket): Boolean {
            val reader = BufferedReader(InputStreamReader(socket.getInputStream(), Charsets.US_ASCII))
            val headers = mutableMapOf<String, String>()
            if (reader.readLine() == null) return false
            while (true) {
                val line = reader.readLine() ?: return false
                if (line.isEmpty()) break
                line.substringBefore(":").trim().lowercase().takeIf { it.isNotEmpty() }?.let { name ->
                    headers[name] = line.substringAfter(":").trim()
                }
            }
            val key = headers["sec-websocket-key"] ?: return false
            val accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1").digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray())
            )
            val writer = BufferedWriter(OutputStreamWriter(socket.getOutputStream(), Charsets.US_ASCII))
            writer.write("HTTP/1.1 101 Switching Protocols\r\n")
            writer.write("Upgrade: websocket\r\n")
            writer.write("Connection: Upgrade\r\n")
            writer.write("Sec-WebSocket-Accept: $accept\r\n\r\n")
            writer.flush()
            return true
        }
    }
}
