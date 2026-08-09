package dev.adrian.showdown

data class ShowdownPrivateMessage(val sender: String, val recipient: String, val text: String)

object ShowdownPrivateMessages {
    fun parse(line: String): ShowdownPrivateMessage? {
        if (!line.startsWith("|pm|")) return null
        val fields = line.removePrefix("|pm|").split('|', limit = 3)
        if (fields.size < 3) return null
        val sender = fields[0].trim()
        val recipient = fields[1].trim()
        val text = fields[2].trim()
        if (sender.isBlank() || recipient.isBlank() || text.isBlank()) return null
        return ShowdownPrivateMessage(sender, recipient, text)
    }

    fun target(message: ShowdownPrivateMessage, localUsername: String): String = if (message.sender.equals(localUsername, true)) message.recipient else message.sender

    fun command(target: String, text: String) = "/pm ${target.trim()}, ${text.trim()}"
}
