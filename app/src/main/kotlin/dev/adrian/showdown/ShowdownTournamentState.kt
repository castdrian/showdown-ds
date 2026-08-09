package dev.adrian.showdown

import org.json.JSONArray
import org.json.JSONObject

class ShowdownTournamentState {
    data class Snapshot(
        val isActive: Boolean = false,
        val format: String = "",
        val teambuilderFormat: String = "",
        val generator: String = "",
        val playerCap: Int = 0,
        val isStarted: Boolean = false,
        val isJoined: Boolean = false,
        val challenges: List<String> = emptyList(),
        val challengeBys: List<String> = emptyList(),
        val challenged: String? = null,
        val challenging: String? = null,
        val bracketData: String? = null,
        val events: List<String> = emptyList()
    )

    var snapshot: Snapshot = Snapshot()
        private set

    fun clear() {
        snapshot = Snapshot()
    }

    fun applyProtocol(line: String): Boolean {
        if (!line.startsWith("|tournament|")) return false
        val fields = line.split('|')
        when (fields.getOrNull(2)?.lowercase()) {
            "create" -> create(fields)
            "update" -> applyUpdate(line)
            "updateend" -> return true
            "join" -> addEvent("${fields.getOrNull(3).orEmpty()} joined the tournament.")
            "leave" -> addEvent("${fields.getOrNull(3).orEmpty()} left the tournament.")
            "replace" -> addEvent("${fields.getOrNull(3).orEmpty()} replaced ${fields.getOrNull(4).orEmpty()}.")
            "start" -> {
                snapshot = snapshot.copy(isStarted = true)
                addEvent("Tournament started with ${fields.getOrNull(3).orEmpty()} players.")
            }
            "disqualify" -> addEvent("${fields.getOrNull(3).orEmpty()} was disqualified.")
            "battlestart" -> addEvent("Tournament battle: ${fields.getOrNull(3).orEmpty()} vs ${fields.getOrNull(4).orEmpty()}.")
            "battleend" -> addBattleEnd(fields)
            "end" -> end(line)
            "forceend" -> {
                snapshot = snapshot.copy(isActive = false)
                addEvent("The tournament was forcibly ended.")
            }
            "scouting" -> addEvent("Scouting ${if (fields.getOrNull(3) == "allow") "allowed" else "banned"}.")
            "autostart" -> addEvent(if (fields.getOrNull(3) == "on") "Automatic start in ${fields.getOrNull(4).orEmpty()} seconds." else "Automatic start disabled.")
            "autodq" -> addEvent(autoDisqualifyMessage(fields))
            "error" -> addEvent(tournamentError(fields.getOrNull(3).orEmpty()))
        }
        return true
    }

    fun bracketLines(): List<String> {
        val json = runCatching { snapshot.bracketData?.let(::JSONObject) }.getOrNull() ?: return emptyList()
        return if (json.optString("type") == "table") tableBracketLines(json) else treeBracketLines(json)
    }

    fun title(): String = snapshot.format.takeIf { it.isNotBlank() }?.let { "$it Tournament" } ?: "Tournament"

    fun status(): String = when {
        !snapshot.isActive && snapshot.format.isNotBlank() -> "Finished"
        snapshot.isStarted -> "In progress"
        snapshot.isActive -> "Signups open"
        else -> "No active tournament"
    }

    private fun create(fields: List<String>) {
        val baseFormat = fields.getOrNull(3).orEmpty()
        val generator = fields.getOrNull(4).orEmpty()
        val playerCap = fields.getOrNull(5)?.toIntOrNull() ?: 0
        val customName = fields.getOrNull(6).orEmpty().trim()
        snapshot = Snapshot(
            isActive = true,
            format = customName.ifBlank { baseFormat },
            teambuilderFormat = baseFormat,
            generator = generator,
            playerCap = playerCap,
            events = listOf("${customName.ifBlank { baseFormat }} tournament created.")
        )
    }

    private fun applyUpdate(line: String) {
        val payload = line.substringAfter("|tournament|update|", "")
        val update = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val current = snapshot
        snapshot = current.copy(
            isActive = true,
            format = update.optString("format", current.format),
            teambuilderFormat = update.optString("teambuilderFormat", current.teambuilderFormat).ifBlank { update.optString("format", current.teambuilderFormat) },
            generator = update.optString("generator", current.generator),
            playerCap = update.optInt("playerCap", current.playerCap),
            isStarted = update.optBoolean("isStarted", current.isStarted),
            isJoined = update.optBoolean("isJoined", current.isJoined),
            challenges = update.optStringList("challenges", current.challenges),
            challengeBys = update.optStringList("challengeBys", current.challengeBys),
            challenged = update.optNullableString("challenged", current.challenged),
            challenging = update.optNullableString("challenging", current.challenging),
            bracketData = update.optJSONObject("bracketData")?.toString() ?: current.bracketData
        )
    }

    private fun end(line: String) {
        val payload = line.substringAfter("|tournament|end|", "")
        val result = runCatching { JSONObject(payload) }.getOrNull() ?: return
        val winners = result.optJSONArray("results")?.let(::jsonStrings).orEmpty()
        val winnerText = winners.joinToString(", ").ifBlank { "No winner recorded" }
        snapshot = snapshot.copy(
            isActive = false,
            isStarted = true,
            format = result.optString("format", snapshot.format),
            generator = result.optString("generator", snapshot.generator),
            bracketData = result.optJSONObject("bracketData")?.toString() ?: snapshot.bracketData
        )
        addEvent("Tournament finished. Winner: $winnerText.")
    }

    private fun addBattleEnd(fields: List<String>) {
        val playerOne = fields.getOrNull(3).orEmpty()
        val playerTwo = fields.getOrNull(4).orEmpty()
        val result = when (fields.getOrNull(5)) {
            "win" -> "$playerOne won"
            "loss" -> "$playerOne lost"
            else -> "Draw"
        }
        val score = fields.getOrNull(6).orEmpty().replace(',', '–')
        val recorded = if (fields.getOrNull(7) == "fail") " Match did not count." else ""
        addEvent("$result against $playerTwo ($score).$recorded")
    }

    private fun autoDisqualifyMessage(fields: List<String>): String = when (fields.getOrNull(3)) {
        "on" -> "Automatic disqualification every ${fields.getOrNull(4).orEmpty()} seconds."
        "target" -> "Respond within ${fields.getOrNull(4).orEmpty()} seconds."
        else -> "Automatic disqualification disabled."
    }

    private fun tournamentError(code: String): String = when (code) {
        "AlreadyStarted", "BracketFrozen" -> "The tournament has already started."
        "NotStarted", "BracketNotFrozen" -> "The tournament has not started yet."
        "UserAlreadyAdded" -> "You are already in the tournament."
        "UserNotAdded" -> "You are not in the tournament."
        "NotEnoughUsers" -> "There are not enough users yet."
        "Full" -> "The tournament is full."
        "UserNotNamed" -> "You need a named account to join."
        "AlreadyDisqualified" -> "You have already been disqualified."
        "Banned" -> "You are banned from entering tournaments."
        else -> code.ifBlank { "Unknown tournament error." }
    }

    private fun tableBracketLines(json: JSONObject): List<String> {
        val headers = json.optJSONObject("tableHeaders") ?: return emptyList()
        val rows = headers.optJSONArray("rows") ?: return emptyList()
        val columns = headers.optJSONArray("cols") ?: return emptyList()
        val contents = json.optJSONArray("tableContents") ?: return emptyList()
        val lines = mutableListOf<String>()
        for (rowIndex in 0 until contents.length()) {
            val row = contents.optJSONArray(rowIndex) ?: continue
            for (columnIndex in 0 until row.length()) {
                val cell = row.optJSONObject(columnIndex) ?: continue
                val state = cell.optString("state")
                if (state == "unavailable") continue
                val left = rows.optString(rowIndex)
                val right = columns.optString(columnIndex)
                val score = cell.optJSONArray("score")?.let(::jsonStrings)?.joinToString("–")?.let { " · $it" }.orEmpty()
                lines += "$left vs $right · ${state.ifBlank { "pending" }}$score"
            }
        }
        return lines
    }

    private fun treeBracketLines(json: JSONObject): List<String> {
        val root = json.optJSONObject("rootNode") ?: return emptyList()
        val lines = mutableListOf<String>()
        collectTreeMatches(root, lines)
        return lines
    }

    private fun collectTreeMatches(node: JSONObject, lines: MutableList<String>) {
        val children = node.optJSONArray("children")
        if (children == null) return
        for (index in 0 until children.length()) collectTreeMatches(children.optJSONObject(index) ?: continue, lines)
        val teams = childrenToTeams(children)
        if (teams.size >= 2 && node.optString("state").isNotBlank()) {
            val score = node.optJSONArray("score")?.let(::jsonStrings)?.joinToString("–")?.let { " · $it" }.orEmpty()
            val winner = node.optString("team").takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
            lines += "${teams[0]} vs ${teams[1]} · ${node.optString("state")}$score$winner"
        }
    }

    private fun childrenToTeams(children: JSONArray): List<String> = buildList {
        for (index in 0 until children.length()) {
            val child = children.optJSONObject(index) ?: continue
            child.optString("team").takeIf { it.isNotBlank() }?.let(::add)
                ?: child.optJSONArray("children")?.let { nested -> childrenToTeams(nested).firstOrNull()?.let(::add) }
        }
    }

    private fun addEvent(text: String) {
        val value = text.trim()
        if (value.isBlank()) return
        snapshot = snapshot.copy(events = (snapshot.events + value).takeLast(12))
    }

    private fun JSONObject.optStringList(key: String, fallback: List<String>): List<String> {
        if (!has(key)) return fallback
        return optJSONArray(key)?.let(::jsonStrings).orEmpty()
    }

    private fun JSONObject.optNullableString(key: String, fallback: String?): String? {
        if (!has(key) || isNull(key)) return if (has(key)) null else fallback
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun jsonStrings(array: JSONArray): List<String> = buildList {
        for (index in 0 until array.length()) array.optString(index).trim().takeIf { it.isNotBlank() }?.let(::add)
    }

    companion object {
        fun joinCommand() = "/tournament join"
        fun leaveCommand() = "/tournament leave"
        fun challengeCommand(username: String) = "/tournament challenge ${username.trim()}"
        fun acceptChallengeCommand() = "/tournament acceptchallenge"
        fun cancelChallengeCommand() = "/tournament cancelchallenge"
        fun validateTeamCommand() = "/tournament vtm"
    }
}
