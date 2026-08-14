package dev.adrian.showdown

object BattleFeedMessageIdentity {
    fun matches(first: String, second: String): Boolean = normalizedText(first) == normalizedText(second)

    private fun normalizedText(value: String): String = value
        .lowercase()
        .replace("restored hp", "recovered health")
        .replace("restored health", "recovered health")
        .replace("recovered hp", "recovered health")
        .replace(Regex("[.!?]+$"), "")
        .replace(Regex("\\s+"), " ")
        .trim()
}
