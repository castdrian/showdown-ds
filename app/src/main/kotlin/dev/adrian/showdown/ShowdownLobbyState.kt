package dev.adrian.showdown

import org.json.JSONObject

class ShowdownLobbyState {
    data class OutgoingChallenge(val username: String, val format: String)
    data class RoomSummary(val id: String, val title: String, val description: String, val userCount: Int, val section: String)
    data class BattleRoomSummary(val id: String, val playerOne: String, val playerTwo: String, val minimumElo: String)
    data class LadderEntry(val username: String, val elo: Double, val gxe: Double, val rpr: Double, val rprd: Double, val coil: Double?)

    private val activeSearches = mutableSetOf<String>()
    private val activeGames = linkedMapOf<String, String>()
    private val pendingChallenges = linkedMapOf<String, String>()
    private val publicRooms = mutableListOf<RoomSummary>()
    private val activeBattleRooms = mutableListOf<BattleRoomSummary>()
    private val activeLadder = mutableListOf<LadderEntry>()

    val games get() = activeGames.toMap()
    val battles get() = activeGames.filterKeys(::isBattleRoom)
    val incomingChallenges get() = pendingChallenges.toMap()
    val rooms get() = publicRooms.toList()
    val battleRooms get() = activeBattleRooms.toList()
    val ladder get() = activeLadder.toList()
    var outgoingChallenge: OutgoingChallenge? = null
        private set

    fun isSearching(format: String) = format in activeSearches

    fun clearSearch(format: String) {
        activeSearches.remove(format)
    }

    fun clear() {
        activeSearches.clear()
        activeGames.clear()
        pendingChallenges.clear()
        publicRooms.clear()
        activeBattleRooms.clear()
        activeLadder.clear()
        outgoingChallenge = null
    }

    fun clearBattle(roomId: String) {
        activeGames.remove(roomId)
    }

    fun clearLadder() {
        activeLadder.clear()
    }

    fun firstNewBattle(previousRoomIds: Set<String>): String? = battles.keys.firstOrNull { it !in previousRoomIds }

    fun battleForReconnect(activeRoomId: String?, pendingRoomId: String?, allowPendingJoinRecovery: Boolean = false): String? {
        if (activeRoomId != null) return null
        if (pendingRoomId != null && !allowPendingJoinRecovery) return null
        return battles.keys.firstOrNull()
    }

    fun applyProtocol(lines: List<String>) {
        lines.forEach { line ->
            val fields = line.split('|')
            when (fields.getOrNull(1)) {
                "updatesearch" -> applySearch(fields.getOrNull(2))
                "updatechallenges" -> applyChallenges(fields.getOrNull(2))
                "queryresponse" -> when (fields.getOrNull(2)) {
                    "rooms" -> applyRooms(fields.getOrNull(3))
                    "roomlist" -> applyBattleRooms(fields.getOrNull(3))
                    "laddertop" -> applyLadder(fields.getOrNull(3))
                }
            }
        }
    }

    private fun applyBattleRooms(payload: String?) {
        val state = runCatching { JSONObject(payload ?: "{}") }.getOrNull() ?: return
        val rooms = state.optJSONObject("rooms") ?: JSONObject()
        val parsed = mutableListOf<BattleRoomSummary>()
        rooms.keys().forEach { id ->
            val room = rooms.optJSONObject(id) ?: return@forEach
            val playerOne = room.optString("p1").trim()
            val playerTwo = room.optString("p2").trim()
            if (playerOne.isBlank() || playerTwo.isBlank()) return@forEach
            val minimumElo = when (val value = room.opt("minElo")) {
                is Number -> value.toInt().toString()
                else -> value?.toString()?.trim().orEmpty()
            }
            parsed += BattleRoomSummary(id, playerOne, playerTwo, minimumElo)
        }
        activeBattleRooms.clear()
        activeBattleRooms += parsed
    }

    private fun applyRooms(payload: String?) {
        val state = runCatching { JSONObject(payload ?: "{}") }.getOrNull() ?: return
        val parsed = mutableListOf<RoomSummary>()
        appendRooms(state.optJSONArray("official"), "Official", parsed)
        appendRooms(state.optJSONArray("pspl"), "Spotlight", parsed)
        appendRooms(state.optJSONArray("chat"), "Chat rooms", parsed)
        publicRooms.clear()
        publicRooms += parsed.distinctBy(RoomSummary::id)
    }

    private fun applyLadder(payload: String?) {
        val state = runCatching { JSONObject(payload ?: "{}") }.getOrNull() ?: return
        val rows = state.optJSONArray("toplist") ?: return
        val parsed = mutableListOf<LadderEntry>()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val username = row.optString("username").trim().ifBlank { row.optString("userid").trim() }
            if (username.isBlank()) continue
            parsed += LadderEntry(
                username = username,
                elo = row.optDouble("elo", 0.0),
                gxe = row.optDouble("gxe", 0.0),
                rpr = row.optDouble("rpr", 0.0),
                rprd = row.optDouble("rprd", 0.0),
                coil = row.optDouble("coil", Double.NaN).takeUnless(Double::isNaN)
            )
        }
        activeLadder.clear()
        activeLadder += parsed
    }

    private fun appendRooms(values: org.json.JSONArray?, section: String, target: MutableList<RoomSummary>) {
        if (values == null) return
        for (index in 0 until values.length()) {
            val room = values.optJSONObject(index) ?: continue
            val title = room.optString("title").trim().ifBlank { room.optString("id").trim() }
            val id = room.optString("id").trim().ifBlank { roomIdFromTitle(title) }
            if (id.isBlank() || title.isBlank()) continue
            target += RoomSummary(
                id = id,
                title = title,
                description = room.optString("desc").trim(),
                userCount = room.optInt("userCount", -1),
                section = room.optString("section").trim().ifBlank { section }
            )
        }
    }

    private fun roomIdFromTitle(title: String) = title.lowercase()
        .replace("[^a-z0-9]".toRegex(), "")

    private fun applySearch(payload: String?) {
        val state = runCatching { JSONObject(payload ?: "{}") }.getOrNull() ?: return
        activeSearches.clear()
        state.optJSONArray("searching")?.let { searches ->
            for (index in 0 until searches.length()) searches.optString(index).takeIf { it.isNotBlank() }?.let(activeSearches::add)
        }
        activeGames.clear()
        state.optJSONObject("games")?.let { games ->
            games.keys().forEach { roomId ->
                activeGames[roomId] = gameDescription(roomId, games.opt(roomId))
            }
        }
    }

    private fun gameDescription(roomId: String, value: Any?): String {
        val battleRoom = isBattleRoom(roomId)
        if (value is JSONObject) {
            value.optString("title").trim().takeIf(String::isNotBlank)?.let { return it }
            val players = listOf(value.optString("p1"), value.optString("p2"))
                .map(String::trim)
                .filter(String::isNotBlank)
            if (players.isNotEmpty()) return players.joinToString(" vs. ")
            value.optString("format").trim().takeIf(String::isNotBlank)?.let { return "${if (battleRoom) "Battle" else "Game"} · $it" }
            return if (battleRoom) "Battle room" else "Game room"
        }
        return value?.toString()?.trim()?.takeIf(String::isNotBlank) ?: if (battleRoom) "Battle room" else "Game room"
    }

    private fun isBattleRoom(roomId: String) = roomId.startsWith("battle-")

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
        fun noInitReason(lines: List<String>): String? = lines.firstOrNull { it.startsWith("|noinit|") }?.let { line ->
            val fields = line.split('|', limit = 4)
            val reason = fields.getOrNull(3)?.trim()?.takeIf { it.isNotBlank() }
                ?: fields.getOrNull(2)?.trim()?.takeIf { it.isNotBlank() }
                ?: "That battle room is no longer available."
            if (reason.contains("does not exist", true)) "That battle room expired. Find another battle." else reason
        }

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

        fun joinBattleCommand(roomId: String) = "/join ${roomId.trim()}"
    }
}
