package dev.adrian.showdown

internal object ShowdownDecisionDelivery {
    private val requestIdPattern = Regex("\"rqid\"\\s*:\\s*(-?\\d+)")

    fun shouldClearPendingCommand(command: String, lines: List<String>): Boolean {
        if (lines.any { it.startsWith("|sentchoice|") || it.startsWith("|error|") }) return true
        val requestLines = lines.filter { it.startsWith("|request|") }
        if (requestLines.isEmpty()) return false
        val pendingRequestId = requestIdFromCommand(command) ?: return true
        return requestLines.any { requestIdFromLine(it) != pendingRequestId }
    }

    private fun requestIdFromCommand(command: String): Long? = command.substringAfterLast('|', "").toLongOrNull()

    private fun requestIdFromLine(line: String): Long? = requestIdPattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
}
