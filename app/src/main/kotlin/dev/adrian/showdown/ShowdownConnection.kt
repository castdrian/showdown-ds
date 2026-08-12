package dev.adrian.showdown

import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener

class ShowdownConnection(
    private val endpoint: ShowdownServerEndpoint,
    private val listener: Listener,
    private val httpClient: OkHttpClient = OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build()
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

    fun connect() {
        disconnect()
        closedSocket = null
        transportReady = false
        usesSockJs = false
        listener.onConnectionStateChanged(State.CONNECTING)
        val request = Request.Builder().url(endpoint.webSocketUrl).build()
        socket = httpClient.newWebSocket(request, SocketListener())
    }

    fun disconnect() {
        val previousSocket = socket
        socket = null
        closedSocket = previousSocket
        transportReady = false
        usesSockJs = false
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

    fun close() {
        disconnect()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private fun sendFrame(message: String): Boolean {
        if (!transportReady) return false
        return socket?.send(ShowdownSocketFrames.encode(message, usesSockJs)) == true
    }

    private fun markTransportReady() {
        if (transportReady) return
        transportReady = true
        listener.onConnectionStateChanged(State.CONNECTED)
    }

    private fun dispatchProtocol(message: String) {
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
        packets.forEach { (packetRoomId, packetLines) -> listener.onProtocol(packetRoomId, packetLines) }
    }

    private fun isCurrent(webSocket: WebSocket): Boolean = socket === webSocket

    private fun markDisconnected(webSocket: WebSocket, detail: String): Boolean {
        if (!isCurrent(webSocket) || closedSocket === webSocket) return false
        closedSocket = webSocket
        transportReady = false
        listener.onConnectionStateChanged(State.DISCONNECTED, detail)
        return true
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) = Unit

        override fun onMessage(webSocket: WebSocket, text: String) {
            if (!isCurrent(webSocket) || closedSocket === webSocket) return
            when (val frame = ShowdownSocketFrames.decode(text)) {
                ShowdownSocketFrame.Open -> {
                    usesSockJs = true
                    markTransportReady()
                }
                is ShowdownSocketFrame.Messages -> {
                    usesSockJs = true
                    markTransportReady()
                    frame.values.forEach(::dispatchProtocol)
                }
                is ShowdownSocketFrame.Closed -> {
                    if (markDisconnected(webSocket, frame.reason)) webSocket.close(1000, frame.reason.ifBlank { "Server closed" })
                }
                is ShowdownSocketFrame.Raw -> {
                    usesSockJs = false
                    markTransportReady()
                    dispatchProtocol(frame.value)
                }
            }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            markDisconnected(webSocket, reason)
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            markDisconnected(webSocket, reason)
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            if (!isCurrent(webSocket) || closedSocket === webSocket) return
            closedSocket = webSocket
            transportReady = false
            listener.onConnectionStateChanged(State.FAILED, throwable.message.orEmpty())
        }
    }
}
