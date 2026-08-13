package dev.adrian.showdown

import java.net.URI

data class ShowdownServerEndpoint(
    val displayName: String,
    val webSocketUrl: String,
    val loginUrl: String = "https://play.pokemonshowdown.com/api/login",
    val registrationUrl: String = loginUrl.substringBeforeLast('/') + "/register",
    val changePasswordUrl: String = loginUrl.substringBeforeLast('/') + "/changepassword"
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
            val loginScheme = if (scheme == "ws") "http" else "https"
            val loginUrl = if (isOfficialHost(host)) {
                "https://play.pokemonshowdown.com/api/login"
            } else {
                "$loginScheme://$host$port/api/login"
            }
            val registrationUrl = if (isOfficialHost(host)) {
                "https://play.pokemonshowdown.com/api/register"
            } else {
                "$loginScheme://$host$port/api/register"
            }
            val changePasswordUrl = if (isOfficialHost(host)) {
                "https://play.pokemonshowdown.com/api/changepassword"
            } else {
                "$loginScheme://$host$port/api/changepassword"
            }
            return ShowdownServerEndpoint(
                host + port,
                "$scheme://$host$port$socketPath",
                loginUrl,
                registrationUrl,
                changePasswordUrl
            )
        }

        private fun isOfficialHost(host: String) = host == "pokemonshowdown.com" ||
            host.endsWith(".pokemonshowdown.com") ||
            host == "psim.us" ||
            host.endsWith(".psim.us")
    }
}
