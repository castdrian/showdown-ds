package com.showdown.ds

import java.net.URI

data class ShowdownServerEndpoint(
    val displayName: String,
    val webSocketUrl: String,
    val loginUrl: String = "https://play.pokemonshowdown.com/api/login"
) {
    companion object {
        val playShowdown = ShowdownServerEndpoint("Pokémon Showdown", "wss://sim3.psim.us/showdown/websocket")
        val emulatorLocal = ShowdownServerEndpoint("This Mac", "ws://10.0.2.2:8000/showdown/websocket")

        fun fromInput(input: String): ShowdownServerEndpoint? {
            val value = input.trim().takeIf { it.isNotEmpty() } ?: return null
            val withScheme = if ("://" in value) value else "wss://$value"
            val uri = runCatching { URI(withScheme) }.getOrNull() ?: return null
            val scheme = when (uri.scheme?.lowercase()) {
                "ws", "wss" -> uri.scheme.lowercase()
                "http" -> "ws"
                "https" -> "wss"
                else -> return null
            }
            val host = uri.host?.takeIf { it.isNotBlank() } ?: return null
            val port = if (uri.port == -1) "" else ":${uri.port}"
            val path = uri.path.orEmpty().trimEnd('/')
            val socketPath = when {
                path.endsWith("/websocket") -> path
                path.endsWith("/showdown") -> "$path/websocket"
                path.isBlank() || path == "/" -> "/showdown/websocket"
                else -> "$path/showdown/websocket"
            }
            return ShowdownServerEndpoint(
                host + port,
                "$scheme://$host$port$socketPath"
            )
        }
    }
}
