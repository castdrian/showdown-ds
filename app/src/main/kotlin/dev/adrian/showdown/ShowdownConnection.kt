package dev.adrian.showdown

import java.util.ArrayDeque
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ShowdownConnection(
    private val endpoint: ShowdownServerEndpoint,
    private val listener: Listener,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build(),
    private val transportReadyTimeoutMillis: Long = TRANSPORT_READY_TIMEOUT_MILLIS
) {
    enum class State {
        DISCONNECTED,
        CONNECTING,
        CONNECTED,
        FAILED
    }

    interface Listener {
        fun onConnectionStateChanged(state: State, detail: String = "")
        fun onProtocol(roomId: String?, lines: List<String>)
    }

    private var socket: WebSocket? = null
    private var closedSocket: WebSocket? = null
    private var transportReady = false
    private var usesSockJs = false
    private var acceptingCommands = false
    private val pendingMessages = ArrayDeque<String>()
    private val sendLock = Any()
    private val watchdog = Executors.newSingleThreadScheduledExecutor { runnable ->
        Thread(runnable, "showdown-connection-watchdog").apply { isDaemon = true }
    }
    private var transportReadyTimeout: ScheduledFuture<*>? = null
    private var activeGeneration = 0L

    private enum class TransportReadyResult {
        STALE,
        READY,
        ALREADY_READY,
        FAILED
    }

    fun connect() {
        val request = Request.Builder().url(endpoint.webSocketUrl).build()
        val (previousSocket, generation) = synchronized(sendLock) {
            val previous = socket
            socket = null
            closedSocket = previous
            transportReady = false
            usesSockJs = false
            acceptingCommands = true
            pendingMessages.clear()
            cancelTransportReadyTimeoutLocked()
            activeGeneration += 1
            val generation = activeGeneration
            socket = httpClient.newWebSocket(request, SocketListener(generation))
            if (!transportReady) {
                transportReadyTimeout = watchdog.schedule(
                    { failTransportReadiness(generation) },
                    transportReadyTimeoutMillis.coerceAtLeast(1L),
                    TimeUnit.MILLISECONDS
                )
            }
            previous to generation
        }
        previousSocket?.close(1000, "Client reconnecting")
        listener.onConnectionStateChanged(State.CONNECTING)
    }

    fun disconnect() {
        val previousSocket = synchronized(sendLock) {
            val previous = socket
            socket = null
            closedSocket = previous
            transportReady = false
            usesSockJs = false
            acceptingCommands = false
            pendingMessages.clear()
            cancelTransportReadyTimeoutLocked()
            activeGeneration += 1
            previous
        }
        previousSocket?.close(1000, "Client closed")
    }

    fun sendGlobal(command: String): Boolean {
        val message = "|${command.removePrefix("|")}"
        return sendFrame(message)
    }

    fun send(roomId: String?, command: String): Boolean {
        val message = roomId?.takeIf { it.isNotBlank() }?.let { "$it|$command" } ?: command
        return sendFrame(message)
    }

    fun isTransportReady(): Boolean = synchronized(sendLock) { transportReady }

    fun close() {
        disconnect()
        watchdog.shutdownNow()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun sendFrame(message: String): Boolean {
        synchronized(sendLock) {
            if (!acceptingCommands) return false
            if (!transportReady) {
                pendingMessages.addLast(message)
                return true
            }
            return socket?.send(ShowdownSocketFrames.encode(message, usesSockJs)) == true
        }
    }

    private fun markTransportReady(webSocket: WebSocket, generation: Long, sockJs: Boolean): TransportReadyResult {
        var stateToReport: Pair<ShowdownConnection.State, String>? = null
        val result = synchronized(sendLock) {
            val result = when {
                !isCurrentLocked(webSocket, generation) -> TransportReadyResult.STALE
                transportReady -> TransportReadyResult.ALREADY_READY
                else -> {
                    usesSockJs = sockJs
                    transportReady = true
                    cancelTransportReadyTimeoutLocked()
                    while (pendingMessages.isNotEmpty()) {
                        val message = pendingMessages.first()
                        if (webSocket.send(ShowdownSocketFrames.encode(message, usesSockJs))) {
                            pendingMessages.removeFirst()
                        } else {
                            break
                        }
                    }
                    if (pendingMessages.isNotEmpty()) {
                        transportReady = false
                        usesSockJs = false
                        acceptingCommands = false
                        closedSocket = webSocket
                        cancelTransportReadyTimeoutLocked()
                        pendingMessages.clear()
                        activeGeneration += 1
                        TransportReadyResult.FAILED
                    } else {
                        TransportReadyResult.READY
                    }
                }
            }
            when (result) {
                TransportReadyResult.READY -> stateToReport = State.CONNECTED to ""
                TransportReadyResult.FAILED -> stateToReport = State.FAILED to "The Showdown connection could not send queued commands."
                else -> Unit
            }
            result
        }
        stateToReport?.let { (state, detail) -> listener.onConnectionStateChanged(state, detail) }
        return result
    }

    private fun dispatchProtocol(webSocket: WebSocket, generation: Long, message: String) {
        if (!isCurrent(webSocket, generation)) return
        val packets = mutableListOf<Pair<String?, MutableList<String>>>()
        var roomId: String? = null
        var lines = mutableListOf<String>()
        message.lineSequence().forEach { line ->
            if (line.startsWith(">")) {
                if (lines.isNotEmpty()) packets += roomId to lines
                roomId = line.drop(1).ifBlank { null }
                lines = mutableListOf()
            } else if (line.isNotEmpty()) {
                lines += line
            }
        }
        if (lines.isNotEmpty()) packets += roomId to lines
        packets.forEach { (packetRoomId, packetLines) ->
            if (isCurrent(webSocket, generation)) listener.onProtocol(packetRoomId, packetLines)
        }
    }

    private fun isCurrent(webSocket: WebSocket, generation: Long): Boolean = synchronized(sendLock) {
        isCurrentLocked(webSocket, generation)
    }

    private fun isCurrentLocked(webSocket: WebSocket, generation: Long): Boolean =
        activeGeneration == generation && socket === webSocket && closedSocket !== webSocket

    private fun markDisconnected(webSocket: WebSocket, generation: Long, detail: String): Boolean {
        val marked = synchronized(sendLock) {
            if (!isCurrentLocked(webSocket, generation)) {
                false
            } else {
                closedSocket = webSocket
                transportReady = false
                acceptingCommands = false
                pendingMessages.clear()
                cancelTransportReadyTimeoutLocked()
                activeGeneration += 1
                true
            }
        }
        if (!marked) return false
        listener.onConnectionStateChanged(State.DISCONNECTED, detail)
        return true
    }

    private fun failTransportReadiness(generation: Long) {
        val timedOutSocket = synchronized(sendLock) {
            val current = socket
            if (current == null || activeGeneration != generation || transportReady || !acceptingCommands) {
                null
            } else {
                socket = null
                closedSocket = current
                transportReady = false
                usesSockJs = false
                acceptingCommands = false
                pendingMessages.clear()
                cancelTransportReadyTimeoutLocked()
                activeGeneration += 1
                current
            }
        } ?: return
        timedOutSocket.close(1000, "Showdown transport readiness timeout")
        listener.onConnectionStateChanged(State.FAILED, "Showdown transport did not become ready in time.")
    }

    private fun cancelTransportReadyTimeoutLocked() {
        transportReadyTimeout?.cancel(false)
        transportReadyTimeout = null
    }

    companion object {
        const val TRANSPORT_READY_TIMEOUT_MILLIS = 15_000L
    }

    private inner class SocketListener(private val generation: Long) : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket, generation)) return
            when (val frame = ShowdownSocketFrames.decode(text)) {
                ShowdownSocketFrame.Open -> {
                    markTransportReady(webSocket, generation, sockJs = true)
                }
                is ShowdownSocketFrame.Messages -> {
                    when (markTransportReady(webSocket, generation, sockJs = true)) {
                        TransportReadyResult.READY, TransportReadyResult.ALREADY_READY -> frame.values.forEach {
                            dispatchProtocol(webSocket, generation, it)
                        }
                        else -> Unit
                    }
                }
                is ShowdownSocketFrame.Closed -> {
                    if (markDisconnected(webSocket, generation, frame.reason)) webSocket.close(1000, frame.reason.ifBlank { "Server closed" })
                }
                is ShowdownSocketFrame.Raw -> {
                    when (markTransportReady(webSocket, generation, sockJs = false)) {
                        TransportReadyResult.READY, TransportReadyResult.ALREADY_READY -> dispatchProtocol(webSocket, generation, frame.value)
                        else -> Unit
                    }
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            markDisconnected(webSocket, generation, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            markDisconnected(webSocket, generation, reason)
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            val marked = synchronized(sendLock) {
                if (!isCurrentLocked(webSocket, generation)) {
                    false
                } else {
                    closedSocket = webSocket
                    transportReady = false
                    acceptingCommands = false
                    pendingMessages.clear()
                    cancelTransportReadyTimeoutLocked()
                    activeGeneration += 1
                    true
                }
            }
            if (!marked) return
            listener.onConnectionStateChanged(State.FAILED, t.message.orEmpty())
        }
    }
}
