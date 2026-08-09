package com.showdown.ds

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ShowdownTeam(
    val id: String,
    val name: String,
    val format: String,
    val packed: String,
    val remoteId: String? = null,
    val remotePrivateKey: String? = null,
    val uploadedPacked: String? = null,
    val uploadedName: String? = null,
    val uploadedFormat: String? = null
) {
    val remoteNeedsUpload: Boolean
        get() = remoteId != null && (
            (uploadedPacked != null && uploadedPacked != packed) ||
                (uploadedName != null && uploadedName != name) ||
                (uploadedFormat != null && uploadedFormat != format)
            )
}

class ShowdownTeamLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("showdown_teams", Context.MODE_PRIVATE)

    fun teams(): List<ShowdownTeam> = runCatching {
        val values = JSONArray(preferences.getString("teams", "[]"))
        buildList {
            for (index in 0 until values.length()) {
                val value = values.getJSONObject(index)
                add(
                    ShowdownTeam(
                        value.getString("id"),
                        value.getString("name"),
                        value.getString("format"),
                        value.getString("packed"),
                        value.optString("remoteId").takeIf(String::isNotBlank),
                        value.optString("remotePrivateKey").takeIf(String::isNotBlank),
                        value.optString("uploadedPacked").takeIf(String::isNotBlank),
                        value.optString("uploadedName").takeIf(String::isNotBlank),
                        value.optString("uploadedFormat").takeIf(String::isNotBlank)
                    )
                )
            }
        }
    }.getOrDefault(emptyList())

    fun save(name: String, format: String, packed: String, id: String = UUID.randomUUID().toString()): ShowdownTeam {
        val previous = teams().firstOrNull { it.id == id }
        val team = ShowdownTeam(
            id,
            name.trim().ifBlank { "Untitled team" },
            format.trim(),
            packed.trim(),
            previous?.remoteId,
            previous?.remotePrivateKey,
            previous?.uploadedPacked,
            previous?.uploadedName,
            previous?.uploadedFormat
        )
        val updated = teams().filterNot { it.id == team.id } + team
        write(updated)
        return team
    }

    fun duplicate(id: String): ShowdownTeam? {
        val storedTeams = teams()
        val original = storedTeams.firstOrNull { it.id == id } ?: return null
        val baseName = "Copy of ${original.name}"
        var copyName = baseName
        var suffix = 2
        while (storedTeams.any { it.name.equals(copyName, true) }) {
            copyName = "$baseName $suffix"
            suffix += 1
        }
        val copy = ShowdownTeam(
            id = UUID.randomUUID().toString(),
            name = copyName,
            format = original.format,
            packed = original.packed
        )
        write(storedTeams + copy)
        return copy
    }

    fun markUploaded(id: String, remoteId: String, privateKey: String?, packed: String) {
        val updated = teams().map { team ->
            if (team.id == id) team.copy(
                remoteId = remoteId,
                remotePrivateKey = privateKey,
                uploadedPacked = packed,
                uploadedName = team.name,
                uploadedFormat = team.format
            )
            else team
        }
        write(updated)
    }

    fun markPrivacy(remoteId: String, privateKey: String?) {
        write(teams().map { team ->
            if (team.remoteId == remoteId) team.copy(remotePrivateKey = privateKey) else team
        })
    }

    fun revertToUploaded(id: String): ShowdownTeam? {
        val current = teams().firstOrNull { it.id == id } ?: return null
        val uploadedPacked = current.uploadedPacked ?: return null
        val reverted = current.copy(
            name = current.uploadedName ?: current.name,
            format = current.uploadedFormat ?: current.format,
            packed = uploadedPacked
        )
        write(teams().map { if (it.id == id) reverted else it })
        return reverted
    }

    fun clearRemoteMetadata() {
        write(teams().map {
            it.copy(
                remoteId = null,
                remotePrivateKey = null,
                uploadedPacked = null,
                uploadedName = null,
                uploadedFormat = null
            )
        })
    }

    fun remove(id: String) {
        val updated = teams().filterNot { it.id == id }
        write(updated)
    }

    fun exportBackup(readable: Boolean): String {
        val storedTeams = teams()
        return if (readable) ShowdownTeamBackupCodec.toText(storedTeams) else ShowdownTeamBackupCodec.pack(storedTeams)
    }

    fun importBackup(backupText: String, fallbackName: String = "Imported team", fallbackFormat: String = "gen9"): List<ShowdownTeam> {
        val imported = ShowdownTeamBackupCodec.parse(backupText, fallbackName, fallbackFormat)
        if (imported.isEmpty()) return emptyList()
        write(teams() + imported)
        return imported
    }

    private fun write(values: List<ShowdownTeam>) {
        preferences.edit().putString("teams", JSONArray().apply {
            values.forEach { value ->
                put(
                    JSONObject()
                        .put("id", value.id)
                        .put("name", value.name)
                        .put("format", value.format)
                        .put("packed", value.packed)
                        .apply {
                            value.remoteId?.let { put("remoteId", it) }
                            value.remotePrivateKey?.let { put("remotePrivateKey", it) }
                            value.uploadedPacked?.let { put("uploadedPacked", it) }
                            value.uploadedName?.let { put("uploadedName", it) }
                            value.uploadedFormat?.let { put("uploadedFormat", it) }
                        }
                )
            }
        }.toString()).apply()
    }
}
