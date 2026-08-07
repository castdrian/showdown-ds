package com.showdown.ds

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

    fun connect() {
        disconnect()
        listener.onConnectionStateChanged(State.CONNECTING)
        val request = Request.Builder().url(endpoint.webSocketUrl).build()
        socket = httpClient.newWebSocket(request, SocketListener())
    }

    fun disconnect() {
        socket?.close(1000, "Client closed")
        socket = null
    }

    fun sendGlobal(command: String): Boolean {
        val message = "|${command.removePrefix("|")}"
        return socket?.send(message) == true
    }

    fun send(roomId: String?, command: String): Boolean {
        val message = roomId?.takeIf { it.isNotBlank() }?.let { "$it|$command" } ?: command
        return socket?.send(message) == true
    }

    fun close() {
        disconnect()
        httpClient.dispatcher.executorService.shutdown()
        httpClient.connectionPool.evictAll()
    }

    private inner class SocketListener : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            listener.onConnectionStateChanged(State.CONNECTED)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            val packets = mutableListOf<Pair<String?, MutableList<String>>>()
            var roomId: String? = null
            var lines = mutableListOf<String>()
            text.lineSequence().forEach { line ->
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

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            listener.onConnectionStateChanged(State.DISCONNECTED, reason)
        }

        override fun onFailure(webSocket: WebSocket, throwable: Throwable, response: Response?) {
            listener.onConnectionStateChanged(State.FAILED, throwable.message.orEmpty())
        }
    }
}
