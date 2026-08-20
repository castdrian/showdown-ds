package dev.adrian.showdown

import java.net.URI
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

data class ShowdownServerEndpoint(
    val displayName: String,
    val webSocketUrl: String,
    val loginUrl: String = "https://play.pokemonshowdown.com/api/login",
    val registrationUrl: String = loginUrl.substringBeforeLast('/') + "/register",
    val changePasswordUrl: String = loginUrl.substringBeforeLast('/') + "/changepassword",
    val upkeepUrl: String = loginUrl.substringBeforeLast('/') + "/upkeep",
    val ladderBaseUrl: String = "https://pokemonshowdown.com"
) {
    fun ladderUrl(format: String): String {
        val encodedFormat = URLEncoder.encode(format.trim(), StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "${ladderBaseUrl.trimEnd('/')}/ladder/$encodedFormat.json"
    }

    fun userUrl(username: String): String {
        val encodedUsername = URLEncoder.encode(username.trim(), StandardCharsets.UTF_8.name()).replace("+", "%20")
        return "${ladderBaseUrl.trimEnd('/')}/users/$encodedUsername.json"
    }

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
            val apiPrefix = when {
                path.endsWith("/showdown/websocket") -> path.removeSuffix("/showdown/websocket")
                path.endsWith("/showdown") -> path.removeSuffix("/showdown")
                path.endsWith("/websocket") -> path.removeSuffix("/websocket")
                path.isBlank() || path == "/" -> ""
                else -> path
            }.trimEnd('/')
            val apiBase = "$loginScheme://$host$port$apiPrefix/api"
            val ladderBaseUrl = if (isOfficialHost(host)) {
                "https://pokemonshowdown.com"
            } else {
                "$loginScheme://$host$port$apiPrefix"
            }
            val loginUrl = if (isOfficialHost(host)) {
                "https://play.pokemonshowdown.com/api/login"
            } else {
                "$apiBase/login"
            }
            val registrationUrl = if (isOfficialHost(host)) {
                "https://play.pokemonshowdown.com/api/register"
            } else {
                "$apiBase/register"
            }
            val changePasswordUrl = if (isOfficialHost(host)) {
                "https://play.pokemonshowdown.com/api/changepassword"
            } else {
                "$apiBase/changepassword"
            }
            return ShowdownServerEndpoint(
                host + port,
                "$scheme://$host$port$socketPath",
                loginUrl,
                registrationUrl,
                changePasswordUrl,
                loginUrl.substringBeforeLast('/') + "/upkeep",
                ladderBaseUrl
            )
        }

        private fun isOfficialHost(host: String) = host == "pokemonshowdown.com" ||
            host.endsWith(".pokemonshowdown.com") ||
            host == "psim.us" ||
            host.endsWith(".psim.us")
    }
}
