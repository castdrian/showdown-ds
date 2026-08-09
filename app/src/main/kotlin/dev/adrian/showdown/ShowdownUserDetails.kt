package dev.adrian.showdown

import org.json.JSONObject

object ShowdownUserDetails {
    data class Room(val id: String, val playerOne: String, val playerTwo: String, val isPrivate: Boolean)

    data class Profile(
        val userid: String,
        val name: String,
        val avatar: String?,
        val status: String,
        val group: String,
        val customGroup: String,
        val autoconfirmed: Boolean,
        val online: Boolean,
        val friended: Boolean,
        val rooms: List<Room>
    )

    fun parse(line: String): Profile? {
        val fields = line.split("|", limit = 4)
        if (fields.size < 4 || fields[1] != "queryresponse" || fields[2] != "userdetails") return null
        val payload = runCatching { JSONObject(fields[3]) }.getOrNull() ?: return null
        val userid = payload.optString("userid").trim().ifBlank { payload.optString("id").trim() }
        val name = payload.optString("name").trim().ifBlank { userid }
        if (userid.isBlank() || name.isBlank()) return null
        val roomsValue = payload.opt("rooms")
        val rooms = if (roomsValue is JSONObject) {
            roomsValue.keys().asSequence().mapNotNull { id ->
                val room = roomsValue.optJSONObject(id) ?: return@mapNotNull null
                Room(
                    id = id,
                    playerOne = room.optString("p1").trim(),
                    playerTwo = room.optString("p2").trim(),
                    isPrivate = room.optBoolean("isPrivate", false)
                )
            }.toList()
        } else {
            emptyList()
        }
        return Profile(
            userid = userid,
            name = name,
            avatar = payload.opt("avatar")?.toString()?.trim()?.takeIf { it.isNotBlank() },
            status = payload.optString("status").trim(),
            group = payload.optString("group").trim(),
            customGroup = payload.optString("customgroup").trim(),
            autoconfirmed = payload.optBoolean("autoconfirmed", false),
            online = roomsValue is JSONObject,
            friended = payload.optBoolean("friended", false),
            rooms = rooms
        )
    }

    fun queryCommand(username: String) = "/cmd userdetails ${username.trim()}"

    fun addFriendCommand(username: String) = "/friend add ${username.trim()}"
}
