package dev.adrian.showdown

object BattleFeedMessageIdentity {
    fun matches(first: String, second: String): Boolean = normalizedText(first) == normalizedText(second)

    fun matchesProtocolFallback(protocolText: String, nativeText: String): Boolean =
        normalizedText(protocolText, true) == normalizedText(nativeText, true)

    private fun normalizedText(value: String, protocolFallback: Boolean = false): String {
        val plainText = value.lowercase().trim().removeSurrounding("(", ")")
        val text = if (protocolFallback) normalizePerspective(plainText) else plainText
        val normalized = text
            .replace(Regex("restored(?:\\s+(?:a little|some|a lot of))?\\s+hp"), "recovered health")
            .replace("had its hp restored", "recovered health")
            .replace("restored health", "recovered health")
            .replace("recovered hp", "recovered health")
            .let(::collapseWhitespace)
            .trimEnd { it == '.' || it == '!' || it == '?' }
        return if (protocolFallback) {
            normalized.replace(Regex("lost\\s+\\d+(?:\\.\\d+)?%\\s+of its health"), "lost some of its hp")
        } else {
            normalized
        }
    }

    private fun normalizePerspective(value: String): String {
        val withoutOpponentPrefix = value.removePrefix("the opposing ")
        val trainerBoundary = withoutOpponentPrefix.indexOf("'s ")
        if (trainerBoundary <= 0) return withoutOpponentPrefix
        val attributedEvent = withoutOpponentPrefix.substring(trainerBoundary + 3)
        val includesPokemonPossessive = attributedEvent.contains("'s ")
        val includesPokemonAction = Regex("\\s+(?:used|lost|was|is|fainted|transformed|recovered|restored|returned|protected|became)\\b")
            .containsMatchIn(attributedEvent)
        return if (includesPokemonPossessive || includesPokemonAction) attributedEvent else withoutOpponentPrefix
    }

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
