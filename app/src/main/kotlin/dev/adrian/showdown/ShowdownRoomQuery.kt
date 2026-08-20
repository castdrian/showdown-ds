package dev.adrian.showdown

object ShowdownRoomQuery {
    fun matches(query: String, id: String, title: String, subtitle: String): Boolean {
        val terms = query.trim()
            .split(Regex("\\s+"))
            .filter(String::isNotBlank)
        if (terms.isEmpty()) return true
        val searchable = listOf(id, title, subtitle).joinToString(" ")
        return terms.all { searchable.contains(it, ignoreCase = true) }
    }
}
