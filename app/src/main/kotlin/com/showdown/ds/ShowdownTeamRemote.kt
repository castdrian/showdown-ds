package com.showdown.ds

import org.json.JSONObject

data class ShowdownTeamUploadResult(val remoteId: String, val privateKey: String?)
data class ShowdownTeamPrivacyUpdate(val remoteId: String, val privateKey: String?)

object ShowdownTeamRemote {
    fun parseUpload(line: String): ShowdownTeamUploadResult? {
        val prefix = "|queryresponse|teamupload|"
        if (!line.startsWith(prefix)) return null
        val value = runCatching { JSONObject(line.removePrefix(prefix)) }.getOrNull() ?: return null
        val remoteId = value.opt("teamid")?.toString()?.takeIf(String::isNotBlank) ?: return null
        val privateKey = value.opt("privacy")?.toString()?.takeUnless { it.isBlank() || it == "null" }
        return ShowdownTeamUploadResult(remoteId, privateKey)
    }

    fun command(action: String, remoteId: String?, name: String, format: String, privateTeam: Boolean, packed: String): String {
        val fields = buildList {
            remoteId?.takeIf(String::isNotBlank)?.let(::add)
            add(name.replace(',', ' ').trim().replace(Regex("\\s+"), " ").ifBlank { "Untitled team" })
            add(format.replace(',', ' ').trim())
            add(if (privateTeam) "1" else "0")
            add(packed)
        }
        return listOf("/teams", action, fields.joinToString(",")).joinToString(" ")
    }

    fun privacyCommand(remoteId: String, privateTeam: Boolean) =
        "/teams setprivacy $remoteId,${if (privateTeam) "yes" else "no"}"

    fun deleteCommand(remoteId: String) = "/teams delete $remoteId"

    fun parsePrivacyUpdate(line: String): ShowdownTeamPrivacyUpdate? {
        val prefix = "|queryresponse|teamupdate|"
        if (!line.startsWith(prefix)) return null
        val value = runCatching { JSONObject(line.removePrefix(prefix)) }.getOrNull() ?: return null
        val remoteId = value.opt("teamid")?.toString()?.takeIf(String::isNotBlank) ?: return null
        val privateKey = value.opt("privacy")?.toString()?.takeUnless { it.isBlank() || it == "null" }
        return ShowdownTeamPrivacyUpdate(remoteId, privateKey)
    }

    fun parseDeleted(line: String): String? =
        Regex("^\\|popup\\|Team ([0-9]+) deleted\\.$").matchEntire(line)?.groupValues?.get(1)

    fun shareUrl(remoteId: String, privateKey: String?): String =
        "https://psim.us/t/$remoteId${privateKey?.let { "-$it" }.orEmpty()}"
}
