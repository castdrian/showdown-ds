package dev.adrian.showdown

object BattleFeedMessageIdentity {
    fun matches(first: String, second: String): Boolean = normalizedText(first) == normalizedText(second)

    fun matchesProtocolFallback(
        protocolText: String,
        nativeText: String,
        trainerNames: Collection<String> = emptyList()
    ): Boolean {
        val nativeVariants = normalizedVariants(nativeText, trainerNames)
        return normalizedVariants(protocolText, trainerNames).any(nativeVariants::contains)
    }

    private fun normalizedVariants(value: String, trainerNames: Collection<String>): Set<String> {
        val plainText = value.lowercase().trim().removeSurrounding("(", ")")
        val perspectiveVariants = buildSet {
            add(plainText)
            trainerNames.asSequence()
                .map { it.trim().lowercase() }
                .filter(String::isNotEmpty)
                .forEach { trainerName ->
                    listOf("$trainerName's ")
                        .filter(plainText::startsWith)
                        .map(plainText::removePrefix)
                        .forEach(::add)
                    "$trainerName sent out "
                        .takeIf(plainText::startsWith)
                        ?.let { prefix -> add("sent out ${plainText.removePrefix(prefix)}") }
                    "$trainerName withdrew "
                        .takeIf(plainText::startsWith)
                        ?.let { prefix -> add("withdrew ${plainText.removePrefix(prefix)}") }
                }
        }
        return perspectiveVariants.flatMapTo(mutableSetOf()) { variant ->
            val normalized = normalizedText(variant, true)
            listOf(normalized) + normalizedSwitchNarrations(normalized)
        }
    }

    private fun normalizedSwitchNarrations(value: String): List<String> {
        val normalized = when {
            value.startsWith("sent out ") -> value
            value.startsWith("go! ") -> "sent out ${value.removePrefix("go! ").removeSuffix("!")}"
            value.endsWith(" was sent out") -> "sent out ${value.removeSuffix(" was sent out")}"
            value.endsWith(", come back") -> "withdrew ${value.removeSuffix(", come back")}"
            value.endsWith(" was withdrawn") -> "withdrew ${value.removeSuffix(" was withdrawn")}"
            else -> Regex("^(.+) went back to .+$")
                .matchEntire(value)
                ?.groupValues
                ?.get(1)
                ?.let { "withdrew $it" }
        } ?: return emptyList()
        val nicknameOnly = Regex("^sent out (.+?) \\([^()]+\\)$")
            .matchEntire(normalized)
            ?.groupValues
            ?.get(1)
            ?.let { "sent out $it" }
        return listOfNotNull(normalized, nicknameOnly)
    }

    private fun normalizedText(value: String, protocolFallback: Boolean = false): String {
        val plainText = value.lowercase().trim().removeSurrounding("(", ")")
        val text = if (protocolFallback) normalizePerspective(plainText) else plainText
        val normalized = text
            .replace(Regex("restored(?:\\s+(?:a little|some|a lot of))?\\s+hp"), "recovered health")
            .replace("had its hp restored", "recovered health")
            .replace("restored health", "recovered health")
            .replace("recovered hp", "recovered health")
            .replace("used its white herb", "returned its stats to normal using its white herb")
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
