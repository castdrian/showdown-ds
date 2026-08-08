package com.showdown.ds

import org.json.JSONObject

object ShowdownAuthentication {
    data class UserUpdate(val username: String, val named: Boolean)

    fun challenge(line: String): String? = line.takeIf { it.startsWith("|challstr|") }?.removePrefix("|challstr|")?.takeIf { it.isNotBlank() }

    fun userUpdate(line: String): UserUpdate? {
        if (!line.startsWith("|updateuser|")) return null
        val fields = line.split('|')
        val rawUsername = fields.getOrNull(2).orEmpty()
        val username = rawUsername.drop(1).trim().takeIf { it.isNotBlank() } ?: return null
        return UserUpdate(username, fields.getOrNull(3) == "1")
    }

    fun serverError(line: String): String? = when {
        line.startsWith("|nametaken|") -> line.split('|').getOrNull(3)?.takeIf { it.isNotBlank() }
        line.startsWith("|popup|") -> line.removePrefix("|popup|").replace("||", "\n").takeIf { it.isNotBlank() }
        else -> null
    }

    fun assertion(response: String): String? = runCatching {
        JSONObject(response.removePrefix("]")).optString("assertion").takeIf { it.isNotBlank() }
    }.getOrNull()

    fun renameCommand(username: String, assertion: String) = "/trn $username,0,$assertion"
}
