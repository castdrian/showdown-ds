package com.showdown.ds

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ShowdownTeam(val id: String, val name: String, val format: String, val packed: String)

class ShowdownTeamLibrary(context: Context) {
    private val preferences = context.getSharedPreferences("showdown_teams", Context.MODE_PRIVATE)

    fun teams(): List<ShowdownTeam> = runCatching {
        val values = JSONArray(preferences.getString("teams", "[]"))
        buildList {
            for (index in 0 until values.length()) {
                val value = values.getJSONObject(index)
                add(ShowdownTeam(value.getString("id"), value.getString("name"), value.getString("format"), value.getString("packed")))
            }
        }
    }.getOrDefault(emptyList())

    fun save(name: String, format: String, packed: String, id: String = UUID.randomUUID().toString()): ShowdownTeam {
        val team = ShowdownTeam(id, name.trim().ifBlank { "Untitled team" }, format, packed.trim())
        val updated = teams().filterNot { it.id == team.id } + team
        write(updated)
        return team
    }

    fun remove(id: String) {
        val updated = teams().filterNot { it.id == id }
        write(updated)
    }

    private fun write(values: List<ShowdownTeam>) {
        preferences.edit().putString("teams", JSONArray().apply {
            values.forEach { value -> put(JSONObject().put("id", value.id).put("name", value.name).put("format", value.format).put("packed", value.packed)) }
        }.toString()).apply()
    }
}
