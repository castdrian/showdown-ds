package dev.adrian.showdown

import org.json.JSONObject

internal object ShowdownDecisionDelivery {
    private val requestIdPattern = Regex("\"rqid\"\\s*:\\s*(-?\\d+)")

    fun shouldClearPendingCommand(command: String, lines: List<String>): Boolean {
        if (lines.any(::isChoiceRejection)) return true
        val requestLines = lines.filter { it.startsWith("|request|") }
        val sentChoiceLines = lines.filter { it.startsWith("|sentchoice|") }
        if (sentChoiceLines.isNotEmpty()) {
            val pendingChoice = choiceFromCommand(command) ?: return true
            if (sentChoiceLines.any { sentChoiceMatches(command, pendingChoice, it, requestLines) }) return true
        }
        if (requestLines.isEmpty()) return false
        val pendingRequestId = requestIdFromCommand(command) ?: return true
        return requestLines.any { isWaitingRequest(it) || requestIdFromLine(it) != pendingRequestId }
    }

    private fun sentChoiceMatches(
        command: String,
        pendingChoice: String,
        line: String,
        requestLines: List<String>
    ): Boolean {
        val payload = line.removePrefix("|sentchoice|").trim()
        val acknowledgedRequestId = payload.substringAfterLast('|').toLongOrNull()
        val acknowledgedChoice = if (acknowledgedRequestId == null) {
            payload
        } else {
            payload.substringBeforeLast('|').trim()
        }
        if (acknowledgedChoice != pendingChoice) return false
        val pendingRequestId = requestIdFromCommand(command) ?: return true
        val currentRequestId = requestLines.lastOrNull()?.let(::requestIdFromLine)
        if (
            acknowledgedRequestId != null &&
            currentRequestId != null &&
            acknowledgedRequestId != currentRequestId
        ) return false
        val acknowledgementRequestId = acknowledgedRequestId ?: currentRequestId ?: return false
        return acknowledgementRequestId == pendingRequestId
    }

    private fun requestIdFromCommand(command: String): Long? = command.substringAfterLast('|', "").toLongOrNull()

    private fun requestIdFromLine(line: String): Long? = requestIdPattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()

    private fun isChoiceRejection(line: String) =
        line.startsWith("|error|[Invalid choice]") || line.startsWith("|error|[Unavailable choice]")

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
}
