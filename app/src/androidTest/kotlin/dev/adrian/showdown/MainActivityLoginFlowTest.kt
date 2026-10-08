package dev.adrian.showdown

import android.content.SharedPreferences
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.Closeable
import java.io.InputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.URLDecoder
import java.security.MessageDigest
import java.util.Base64
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLoginFlowTest {
    @Test
    fun signsInAgainstShowdownChallengeThenSendsQueuedLobbyWork() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val serverPreferences = context.getSharedPreferences("showdown", 0)
        val livePreferences = context.getSharedPreferences("showdown_live", 0)
        val credentialPreferences = context.getSharedPreferences("showdown_credentials", 0)
        val sessionPreferences = context.getSharedPreferences("showdown_session", 0)
        val previousPreferences = listOf(serverPreferences, livePreferences, credentialPreferences, sessionPreferences)
            .map { it to it.all }
        val server = LocalShowdownServer()
        var activeScenario: ActivityScenario<MainActivity>? = null

        try {
            serverPreferences.edit().putString("server_endpoint", "ws://127.0.0.1:${server.port}/showdown/websocket").commit()
            livePreferences.edit()
                .clear()
                .putBoolean("maintain_connection", true)
                .putString("pending_lobby_commands", "/cmd rooms")
                .putString("reconnect_lobby_commands", "/cmd rooms")
                .putString("pending_lobby_status", "Loading public rooms…")
                .commit()
            ShowdownCredentialsStore(context).save(ShowdownCredentials("TestUser", "integration-password"))

            activeScenario = ActivityScenario.launch(MainActivity::class.java)

            val loginRequest = server.awaitLoginRequest()
            assertEquals("POST", loginRequest.method)
            assertEquals("/api/login", loginRequest.target)
            val loginFields = parseForm(loginRequest.body)
            assertEquals("TestUser", loginFields["name"])
            assertEquals("integration-password", loginFields["pass"])
            assertEquals("91|local-challenge", loginFields["challstr"])

            val webSocket = server.awaitWebSocket()
            val renameCommand = server.awaitCommand()
            assertEquals("|/trn TestUser,0,local-signed-assertion", renameCommand)
            assertEquals("active", ShowdownSessionStore(context).load()["showdown-test-session"])
            server.sendText(webSocket, "|updateuser| TestUser|1|0|0")
            assertEquals("|/cmd rooms", server.awaitCommand())
        } finally {
            activeScenario?.close()
            server.close()
            previousPreferences.forEach { (preferences, values) -> restorePreferences(preferences, values) }
        }
    }

    private fun parseForm(body: String): Map<String, String> = body.split('&')
        .filter(String::isNotBlank)
        .associate { pair ->
            val key = pair.substringBefore('=')
            val value = pair.substringAfter('=', "")
            URLDecoder.decode(key, Charsets.UTF_8.name()) to URLDecoder.decode(value, Charsets.UTF_8.name())
        }

    private fun restorePreferences(preferences: SharedPreferences, values: Map<String, *>) {
        preferences.edit().clear().apply {
            values.forEach { (key, value) ->
                when (value) {
                    is String -> putString(key, value)
                    is Boolean -> putBoolean(key, value)
                    is Int -> putInt(key, value)
                    is Long -> putLong(key, value)
                    is Float -> putFloat(key, value)
                    is Set<*> -> putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }.commit()
    }

    private data class HttpRequest(val method: String, val target: String, val body: String)

    private class LocalShowdownServer : Closeable {
        private val listener = ServerSocket().apply {
            bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0))
        }
        private val running = AtomicBoolean(true)
        private val sockets = CopyOnWriteArrayList<Socket>()
        private val clients = LinkedBlockingQueue<Socket>()
        private val loginRequests = LinkedBlockingQueue<HttpRequest>()
        private val commands = LinkedBlockingQueue<String>()
        private val workers = CopyOnWriteArrayList<Thread>()
        private val acceptThread = thread(start = true, name = "showdown-login-test-accept") {
            while (running.get()) {
                val socket = runCatching { listener.accept() }.getOrNull() ?: continue
                sockets += socket
                workers += thread(start = true, name = "showdown-login-test-client") { handle(socket) }
            }
        }

        val port: Int
            get() = listener.localPort

        fun awaitWebSocket(): Socket = clients.poll(10, TimeUnit.SECONDS)
            ?: error("The activity did not open its Showdown WebSocket")

        fun awaitLoginRequest(): HttpRequest = loginRequests.poll(10, TimeUnit.SECONDS)
            ?: error("The activity did not submit the Showdown login request")

        fun awaitCommand(): String = commands.poll(10, TimeUnit.SECONDS)
            ?: error("The activity did not send the expected Showdown command")

        fun sendText(socket: Socket, value: String) {
            val bytes = value.toByteArray(Charsets.UTF_8)
            val output = socket.getOutputStream()
            output.write(0x81)
            when {
                bytes.size < 126 -> output.write(bytes.size)
                bytes.size <= 0xffff -> {
                    output.write(126)
                    output.write(bytes.size shr 8)
                    output.write(bytes.size and 0xff)
                }
                else -> error("The test server payload is too large")
            }
            output.write(bytes)
            output.flush()
        }

        private fun handle(socket: Socket) {
            runCatching {
                val request = readHttpRequest(socket)
                if (request.target == "/showdown/websocket") {
                    completeWebSocketHandshake(socket, request.body)
                } else if (request.target == "/api/login") {
                    loginRequests += request
                    sendHttpResponse(
                        socket,
                        "{\"actionsuccess\":true,\"assertion\":\"local-signed-assertion\",\"curuser\":{\"username\":\"TestUser\",\"loggedin\":true}}",
                        "Set-Cookie: showdown-test-session=active; Path=/; HttpOnly\r\n"
                    )
                } else {
                    sendHttpResponse(socket, "{}", status = "404 Not Found")
                }
            }
        }

        private fun completeWebSocketHandshake(socket: Socket, rawHeaders: String) {
            val key = rawHeaders.lineSequence()
                .firstOrNull { it.startsWith("Sec-WebSocket-Key:", true) }
                ?.substringAfter(':')
                ?.trim()
                ?: error("The WebSocket request did not include a key")
            val accept = Base64.getEncoder().encodeToString(
                MessageDigest.getInstance("SHA-1")
                    .digest((key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII))
            )
            socket.getOutputStream().bufferedWriter(Charsets.US_ASCII).apply {
                write("HTTP/1.1 101 Switching Protocols\r\n")
                write("Upgrade: websocket\r\n")
                write("Connection: Upgrade\r\n")
                write("Sec-WebSocket-Accept: $accept\r\n\r\n")
                flush()
            }
            clients += socket
            sendText(socket, "|challstr|91|local-challenge")
            while (running.get() && !socket.isClosed) {
                val text = readWebSocketText(socket.getInputStream()) ?: return
                commands += text
            }
        }

        private fun readHttpRequest(socket: Socket): HttpRequest {
            val input = socket.getInputStream()
            val headerBytes = ByteArrayOutputStream()
            var marker = 0
            while (marker != 4) {
                val byte = input.read()
                if (byte < 0) error("The client closed before completing its HTTP headers")
                headerBytes.write(byte)
                marker = when {
                    marker == 0 && byte == '\r'.code -> 1
                    marker == 1 && byte == '\n'.code -> 2
                    marker == 2 && byte == '\r'.code -> 3
                    marker == 3 && byte == '\n'.code -> 4
                    byte == '\r'.code -> 1
                    else -> 0
                }
            }
            val headers = headerBytes.toString(Charsets.US_ASCII.name())
            val firstLine = headers.lineSequence().first()
            val contentLength = headers.lineSequence()
                .firstOrNull { it.startsWith("Content-Length:", true) }
                ?.substringAfter(':')
                ?.trim()
                ?.toIntOrNull() ?: 0
            val bodyBytes = ByteArray(contentLength)
            readFully(input, bodyBytes)
            return HttpRequest(firstLine.substringBefore(' '), firstLine.split(' ').getOrElse(1) { "" }, bodyBytes.toString(Charsets.UTF_8))
                .let { request -> if (request.target == "/showdown/websocket") request.copy(body = headers) else request }
        }

        private fun readWebSocketText(input: InputStream): String? {
            val first = input.read()
            val second = input.read()
            if (first < 0 || second < 0) return null
            val opcode = first and 0x0f
            if (opcode == 0x8) return null
            var length = second and 0x7f
            if (length == 126) length = input.read() shl 8 or input.read()
            if (length == 127) error("The WebSocket test payload is too large")
            val mask = if (second and 0x80 != 0) ByteArray(4).also { readFully(input, it) } else ByteArray(0)
            val payload = ByteArray(length).also { readFully(input, it) }
            if (mask.isNotEmpty()) payload.indices.forEach { index ->
                payload[index] = (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
            }
            return payload.toString(Charsets.UTF_8)
        }

        private fun readFully(input: InputStream, buffer: ByteArray) {
            var offset = 0
            while (offset < buffer.size) {
                val count = input.read(buffer, offset, buffer.size - offset)
                if (count < 0) error("The client closed before completing its request")
                offset += count
            }
        }

        private fun sendHttpResponse(socket: Socket, body: String, cookieHeader: String = "", status: String = "200 OK") {
            val bytes = body.toByteArray(Charsets.UTF_8)
            socket.getOutputStream().bufferedWriter(Charsets.US_ASCII).apply {
                write("HTTP/1.1 $status\r\n")
                write("Content-Type: application/json\r\n")
                write("Content-Length: ${bytes.size}\r\n")
                write("Connection: close\r\n")
                write(cookieHeader)
                write("\r\n")
                flush()
            }
            socket.getOutputStream().apply {
                write(bytes)
                flush()
            }
            socket.close()
        }

        override fun close() {
            if (!running.compareAndSet(true, false)) return
            listener.close()
            sockets.forEach { runCatching { it.close() } }
            runCatching { acceptThread.join(500) }
            workers.forEach { runCatching { it.join(500) } }
        }
    }
}
