package com.showdown.ds

class ShowdownChatRoomState {
    data class Message(val speaker: String, val text: String, val system: Boolean = false)

    private val roomUsers = mutableListOf<String>()
    private val roomMessages = mutableListOf<Message>()
    val tournament = ShowdownTournamentState()

    var roomId: String? = null
        private set
    var title: String = "Showdown room"
        private set

    val users get() = roomUsers.toList()
    val messages get() = roomMessages.toList()

    fun clear() {
        roomId = null
        title = "Showdown room"
        roomUsers.clear()
        roomMessages.clear()
        tournament.clear()
    }

    fun applyProtocol(packetRoomId: String, lines: List<String>): Boolean {
        if (packetRoomId.isBlank() || packetRoomId.startsWith("battle-")) return false
        if (roomId != packetRoomId || lines.any { it == "|init|chat" }) reset(packetRoomId)
        var changed = false
        lines.forEach { line ->
            if (tournament.applyProtocol(line)) {
                changed = true
                return@forEach
            }
            val fields = line.split('|', limit = 6)
            when (fields.getOrNull(1)) {
                "init" -> changed = changed || fields.getOrNull(2) == "chat"
                "title" -> {
                    title = fields.getOrNull(2).orEmpty().trim().ifBlank { title }
                    changed = true
                }
                "users" -> {
                    roomUsers.clear()
                    fields.getOrNull(2).orEmpty().split(',')
                        .drop(1)
                        .map(String::trim)
                        .filter(String::isNotBlank)
                        .forEach(roomUsers::add)
                    changed = true
                }
                "c" -> {
                    addMessage(fields.getOrNull(2).orEmpty(), fields.getOrNull(3).orEmpty())
                    changed = true
                }
                "c:" -> {
                    addMessage(fields.getOrNull(3).orEmpty(), fields.getOrNull(4).orEmpty())
                    changed = true
                }
                "j", "J" -> {
                    addUser(fields.getOrNull(2).orEmpty())
                    changed = true
                }
                "l", "L" -> {
                    roomUsers.remove(fields.getOrNull(2).orEmpty())
                    changed = true
                }
                "error" -> {
                    addSystemMessage(fields.drop(2).joinToString("|").trim())
                    changed = true
                }
                "raw", "html" -> {
                    addSystemMessage(stripMarkup(fields.drop(2).joinToString("|")))
                    changed = true
                }
            }
        }
        return changed
    }

    private fun reset(id: String) {
        roomId = id
        title = id.replace('-', ' ').replaceFirstChar { it.uppercase() }
        roomUsers.clear()
        roomMessages.clear()
    }

    private fun addUser(identity: String) {
        val value = identity.trim()
        if (value.isNotBlank() && value !in roomUsers) roomUsers += value
    }

    private fun addMessage(speaker: String, text: String) {
        val message = Message(speaker.trim().trimStart('~', '&', '@', '%', '+'), text.trim())
        if (message.text.isNotBlank()) appendMessage(message)
    }

    private fun addSystemMessage(text: String) {
        text.trim().takeIf { it.isNotBlank() }?.let { appendMessage(Message("System", it, true)) }
    }

    private fun appendMessage(message: Message) {
        roomMessages += message
        if (roomMessages.size > 100) roomMessages.removeAt(0)
    }

    private fun stripMarkup(value: String) = value
        .replace(Regex("(?is)<script.*?</script>"), " ")
        .replace(Regex("<[^>]*>"), " ")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()
}
