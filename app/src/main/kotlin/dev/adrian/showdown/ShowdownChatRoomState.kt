package dev.adrian.showdown

class ShowdownChatRoomState {
    data class Message(val speaker: String, val text: String, val system: Boolean = false)

    private val roomUsers = mutableListOf<String>()
    private val roomMessages = mutableListOf<Message>()
    private val namedHtmlMessages = mutableMapOf<String, Message>()
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
        namedHtmlMessages.clear()
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
            if (line.startsWith("||")) {
                addSystemMessage(line.drop(2))
                changed = true
                return@forEach
            }
            if (!line.startsWith("|")) {
                addSystemMessage(line)
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
                "c", "chat" -> {
                    addMessage(fields.getOrNull(2).orEmpty(), fields.drop(3).joinToString("|"))
                    changed = true
                }
                "c:" -> {
                    addMessage(fields.getOrNull(3).orEmpty(), fields.drop(4).joinToString("|"))
                    changed = true
                }
                "j", "J", "join" -> {
                    addUser(fields.getOrNull(2).orEmpty())
                    changed = true
                }
                "l", "L", "leave" -> {
                    removeUser(fields.getOrNull(2).orEmpty())
                    changed = true
                }
                "n", "N", "name" -> {
                    replaceUser(fields.getOrNull(2).orEmpty(), fields.getOrNull(3).orEmpty())
                    changed = true
                }
                "b", "B", "battle" -> {
                    val playerOne = displayName(fields.getOrNull(3))
                    val playerTwo = displayName(fields.getOrNull(4))
                    if (playerOne.isNotBlank() && playerTwo.isNotBlank()) addSystemMessage("$playerOne and $playerTwo started a battle.")
                    changed = true
                }
                "message" -> {
                    addSystemMessage(stripMarkup(fields.drop(2).joinToString("|")))
                    changed = true
                }
                "notify" -> {
                    val title = fields.getOrNull(2).orEmpty().trim()
                    val message = fields.getOrNull(3).orEmpty().trim()
                    addSystemMessage(listOf(title, message).filter(String::isNotBlank).joinToString(": "))
                    changed = true
                }
                "popup" -> {
                    addSystemMessage(line.removePrefix("|popup|").replace("||", "\n"))
                    changed = true
                }
                "uhtml" -> {
                    updateHtmlMessage(fields.getOrNull(2).orEmpty(), fields.drop(3).joinToString("|"), true)
                    changed = true
                }
                "uhtmlchange" -> {
                    updateHtmlMessage(fields.getOrNull(2).orEmpty(), fields.drop(3).joinToString("|"), false)
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
        namedHtmlMessages.clear()
    }

    private fun addUser(identity: String) {
        val value = identity.trim()
        if (value.isNotBlank() && roomUsers.none { userId(it) == userId(value) }) roomUsers += value
    }

    private fun removeUser(identity: String) {
        val id = userId(identity)
        roomUsers.removeAll { userId(it) == id }
    }

    private fun replaceUser(identity: String, oldId: String) {
        val value = identity.trim()
        if (value.isBlank()) return
        val index = roomUsers.indexOfFirst { userId(it) == userId(oldId) }
        if (index >= 0) roomUsers[index] = value else addUser(value)
    }

    private fun userId(identity: String) = normalizedIdentity(identity)
        .replace(" ", "")
        .lowercase()

    private fun displayName(identity: String?): String = normalizedIdentity(identity.orEmpty())

    private fun normalizedIdentity(identity: String): String = identity.trim()
        .dropWhile { it in "~&#%@*☆★+^‽! " }
        .substringBefore('@')

    private fun addMessage(speaker: String, text: String) {
        val message = Message(displayName(speaker), text.trim())
        if (message.text.isNotBlank()) appendMessage(message)
    }

    private fun addSystemMessage(text: String) {
        text.trim().takeIf { it.isNotBlank() }?.let { appendMessage(Message("System", it, true)) }
    }

    private fun updateHtmlMessage(name: String, value: String, createIfMissing: Boolean) {
        val previous = namedHtmlMessages[name]
        val index = previous?.let { roomMessages.indexOfFirst { item -> item === it } } ?: -1
        namedHtmlMessages.remove(name)
        val text = stripMarkup(value)
        if (text.isBlank()) {
            if (index >= 0) roomMessages.removeAt(index)
            trimNamedHtmlMessages()
            return
        }
        if (index < 0 && !createIfMissing) return
        val message = Message("System", text, true)
        if (index >= 0) {
            roomMessages[index] = message
        } else {
            appendMessage(message)
        }
        namedHtmlMessages[name] = message
        trimNamedHtmlMessages()
    }

    private fun trimNamedHtmlMessages() {
        namedHtmlMessages.entries.removeAll { entry -> roomMessages.none { item -> item === entry.value } }
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
        .replace("&rarr;", "→")
        .replace("&larr;", "←")
        .replace("&ndash;", "–")
        .replace("&mdash;", "—")
        .replace(Regex("\\s+"), " ")
        .trim()
}
