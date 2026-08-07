package com.showdown.ds

class ShowdownSearchFlow {
    private var pendingFormat: String? = null
    private var awaitingIdentity = false

    fun begin(formatId: String) {
        pendingFormat = formatId.takeIf { it.isNotBlank() }
        awaitingIdentity = false
    }

    fun onTransportConnected(preferredUsername: String): List<String> {
        if (pendingFormat == null) return emptyList()
        awaitingIdentity = true
        return listOf("/trn ${normalizeUsername(preferredUsername)},0,")
    }

    fun onProtocol(lines: List<String>): List<String> {
        if (!awaitingIdentity || lines.none { it.startsWith("|updateuser|") }) return emptyList()
        awaitingIdentity = false
        val formatId = pendingFormat ?: return emptyList()
        pendingFormat = null
        return listOf("/search $formatId")
    }

    fun cancel() {
        pendingFormat = null
        awaitingIdentity = false
    }

    companion object {
        fun normalizeUsername(value: String): String {
            val filtered = value.filter { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }
                .trim()
                .replace(Regex("\\s+"), " ")
                .take(18)
            return filtered.ifBlank { "ShowdownDS" }
        }
    }
}
