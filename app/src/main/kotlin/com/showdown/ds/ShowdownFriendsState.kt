package com.showdown.ds

class ShowdownFriendsState {
    data class Snapshot(val title: String, val text: String, val error: String?)

    private var current = Snapshot("Friends", "Loading friends…", null)

    val snapshot: Snapshot
        get() = current

    fun clear() {
        current = Snapshot("Friends", "Loading friends…", null)
    }

    fun applyProtocol(roomId: String?, lines: List<String>): Boolean {
        if (roomId?.startsWith("view-friends") != true) return false
        var changed = false
        lines.forEach { line ->
            when {
                line.startsWith("|title|") -> {
                    current = current.copy(title = line.removePrefix("|title|").trim().ifBlank { "Friends" }, error = null)
                    changed = true
                }
                line.startsWith("|pagehtml|") -> {
                    current = current.copy(text = toReadableText(line.removePrefix("|pagehtml|")), error = null)
                    changed = true
                }
                line.startsWith("|error|") -> {
                    current = current.copy(error = line.removePrefix("|error|").trim(), text = "")
                    changed = true
                }
            }
        }
        return changed
    }

    private fun toReadableText(html: String): String = html
        .replace(Regex("<br\\s*/?>"), "\n")
        .replace(Regex("</(?:p|h[1-6]|div|li|form|hr|tr)>"), "\n")
        .replace(Regex("<[^>]+>"), "")
        .replace("&nbsp;", " ")
        .replace("&amp;", "&")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&#39;", "'")
        .replace("&quot;", "\"")
        .replace(Regex("[ \\t]+"), " ")
        .replace(Regex("\\n[ \\t]+"), "\n")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()

    companion object {
        fun pageCommand(page: String = "all") = "/join view-friends-${page.trim().ifBlank { "all" }}"
        fun publicListCommand(username: String) = "/join view-friends-viewuser-${username.trim().lowercase().filter { it in 'a'..'z' || it in '0'..'9' }}"
        fun addCommand(username: String) = "/friend add ${username.trim()}"
        fun removeCommand(username: String) = "/friend remove ${username.trim()}"
        fun acceptCommand(username: String) = "/friends accept ${username.trim()}"
        fun rejectCommand(username: String) = "/friends reject ${username.trim()}"
    }
}
