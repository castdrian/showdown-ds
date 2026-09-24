package dev.adrian.showdown

import org.json.JSONObject

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
        return requestLines.any { isWaitingRequest(it) || requestIdFromLine(it) != pendingRequestId }
    }

    private fun requestIdFromCommand(command: String): Long? = command.substringAfterLast('|', "").toLongOrNull()

    private fun requestIdFromLine(line: String): Long? = requestIdPattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun isWaitingRequest(line: String): Boolean {
        val payload = line.removePrefix("|request|").trim()
        if (payload.equals("null", true)) return true
        val request = runCatching { JSONObject(payload) }.getOrNull() ?: return false
        return request.optBoolean("wait") || request.optString("requestType").equals("wait", true)
    }

    private fun choiceFromCommand(command: String): String? {
        val choice = command.removePrefix("/choose ").substringBeforeLast('|').trim()
        return choice.takeIf(String::isNotEmpty)
    }

    private fun choiceFromSentChoiceLine(line: String): String =
        line.removePrefix("|sentchoice|").substringBeforeLast('|').trim()
}
