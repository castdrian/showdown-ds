package com.showdown.ds

import org.json.JSONObject

class ShowdownLobbyState {
    data class OutgoingChallenge(val username: String, val format: String)

    private val activeSearches = mutableSetOf<String>()
    private val activeBattles = linkedMapOf<String, String>()
    private val pendingChallenges = linkedMapOf<String, String>()

    val battles get() = activeBattles.toMap()
    val incomingChallenges get() = pendingChallenges.toMap()
    var outgoingChallenge: OutgoingChallenge? = null
        private set

    fun isSearching(format: String) = format in activeSearches

    fun clearSearch(format: String) {
        activeSearches.remove(format)
    }

    fun applyProtocol(lines: List<String>) {
        lines.forEach { line ->
            val fields = line.split('|')
            when (fields.getOrNull(1)) {
                "updatesearch" -> applySearch(fields.getOrNull(2))
                "updatechallenges" -> applyChallenges(fields.getOrNull(2))
            }
        }
    }

    private fun applySearch(payload: String?) {
        val state = runCatching { JSONObject(payload ?: "{}") }.getOrNull() ?: return
        activeSearches.clear()
        state.optJSONArray("searching")?.let { searches ->
            for (index in 0 until searches.length()) searches.optString(index).takeIf { it.isNotBlank() }?.let(activeSearches::add)
        }
        activeBattles.clear()
        state.optJSONObject("games")?.let { games ->
            games.keys().forEach { roomId -> activeBattles[roomId] = games.optString(roomId, roomId) }
        }
    }

    private fun applyChallenges(payload: String?) {
        val state = runCatching { JSONObject(payload ?: "{}") }.getOrNull() ?: return
        pendingChallenges.clear()
        state.optJSONObject("challengesFrom")?.let { challenges ->
            challenges.keys().forEach { username -> challenges.optString(username).takeIf { it.isNotBlank() }?.let { pendingChallenges[username] = it } }
        }
        outgoingChallenge = state.optJSONObject("challengeTo")?.let { challenge ->
            OutgoingChallenge(challenge.optString("to"), challenge.optString("format")).takeIf { it.username.isNotBlank() && it.format.isNotBlank() }
        }
    }

    companion object {
        fun searchCommands(format: String, packedTeam: String?) = listOf("/utm ${packedTeam?.takeIf { it.isNotBlank() } ?: "null"}", "/search $format")

        fun challengeCommands(username: String, format: String, packedTeam: String?) = listOf(
            "/utm ${packedTeam?.takeIf { it.isNotBlank() } ?: "null"}",
            "/challenge ${username.trim()}, $format"
        )

        fun acceptChallengeCommands(username: String, packedTeam: String?) = listOf(
            "/utm ${packedTeam?.takeIf { it.isNotBlank() } ?: "null"}",
            "/accept ${username.trim()}"
        )

        fun rejectChallengeCommand(username: String) = "/reject ${username.trim()}"

        fun cancelChallengeCommand(username: String) = "/cancelchallenge ${username.trim()}"

        fun cancelSearchCommand() = "/cancelsearch"
    }
}
