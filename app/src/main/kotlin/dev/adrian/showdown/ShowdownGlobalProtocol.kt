package dev.adrian.showdown

object ShowdownGlobalProtocol {
    data class Notification(val title: String, val message: String)

    fun notification(line: String): Notification? {
        if (!line.startsWith("|notify|")) return null
        val fields = line.split('|', limit = 5)
        val title = fields.getOrNull(2).orEmpty().trim()
        val message = fields.getOrNull(3).orEmpty()
            .replace("||", "\n")
            .trim()
        if (message.isBlank()) return null
        return Notification(title, message)
    }
}
