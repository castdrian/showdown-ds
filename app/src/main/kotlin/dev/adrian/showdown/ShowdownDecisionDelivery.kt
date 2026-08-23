package dev.adrian.showdown

internal object ShowdownDecisionDelivery {
    private val requestIdPattern = Regex("\"rqid\"\\s*:\\s*(-?\\d+)")

    fun shouldClearPendingCommand(command: String, lines: List<String>): Boolean {
        if (lines.any { it.startsWith("|error|") }) return true
        val sentChoiceLines = lines.filter { it.startsWith("|sentchoice|") }
        if (sentChoiceLines.isNotEmpty()) {
            val pendingChoice = choiceFromCommand(command) ?: return true
            if (sentChoiceLines.any { choiceFromSentChoiceLine(it) == pendingChoice }) return true
        }
        val requestLines = lines.filter { it.startsWith("|request|") }
        if (requestLines.isEmpty()) return false
        val pendingRequestId = requestIdFromCommand(command) ?: return true
        return requestLines.any { requestIdFromLine(it) != pendingRequestId }
    }

    private fun requestIdFromCommand(command: String): Long? = command.substringAfterLast('|', "").toLongOrNull()

    private fun requestIdFromLine(line: String): Long? = requestIdPattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun choiceFromCommand(command: String): String? {
        val choice = command.removePrefix("/choose ").substringBeforeLast('|').trim()
        return choice.takeIf(String::isNotEmpty)
    }

    private fun choiceFromSentChoiceLine(line: String): String =
        line.removePrefix("|sentchoice|").substringBeforeLast('|').trim()
}
