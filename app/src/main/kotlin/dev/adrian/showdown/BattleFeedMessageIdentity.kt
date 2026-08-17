package dev.adrian.showdown

object BattleFeedMessageIdentity {
    fun matches(first: String, second: String): Boolean = normalizedText(first) == normalizedText(second)

    private fun normalizedText(value: String): String = value
        .lowercase()
        .replace("restored hp", "recovered health")
        .replace("restored health", "recovered health")
        .replace("recovered hp", "recovered health")
        .let(::collapseWhitespace)
        .trimEnd { it == '.' || it == '!' || it == '?' }

    private fun collapseWhitespace(value: String): String {
        val collapsed = StringBuilder(value.length)
        var pendingSpace = false
        value.forEach { character ->
            if (character.isWhitespace()) {
                pendingSpace = collapsed.isNotEmpty()
            } else {
                if (pendingSpace) collapsed.append(' ')
                collapsed.append(character)
                pendingSpace = false
            }
        }
        return collapsed.toString()
    }
}
