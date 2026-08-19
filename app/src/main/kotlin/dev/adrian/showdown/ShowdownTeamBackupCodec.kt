package dev.adrian.showdown

import java.util.UUID

object ShowdownTeamBackupCodec {
    fun pack(teams: List<ShowdownTeam>): String = teams.joinToString("\n") { team ->
        val metadata = normalizeMetadata(team)
        "${metadata.format}]${metadata.path}|${team.packed.trim()}"
    }

    fun toText(teams: List<ShowdownTeam>): String = teams.joinToString("\n\n") { team ->
        val metadata = normalizeMetadata(team)
        "=== [${metadata.format}] ${metadata.path} ===\n\n${ShowdownTeamCodec.toText(ShowdownTeamCodec.unpack(team.packed))}"
    }

    fun parse(value: String, fallbackName: String = "Imported team", fallbackFormat: String = "gen9"): List<ShowdownTeam> {
        val input = value.trim()
        if (input.isBlank()) return emptyList()
        val packedLines = input.lines().map(String::trim).filter(String::isNotBlank)
        val packedTeams = packedLines.mapNotNull(::parsePackedLine)
        if (packedTeams.size == packedLines.size && packedTeams.isNotEmpty()) return packedTeams
        if (packedLines.any(::looksLikePackedBackupLine)) return emptyList()

        val readableTeams = parseReadable(input)
        if (readableTeams.isNotEmpty()) return readableTeams

        val sets = ShowdownTeamCodec.parse(input)
        if (sets.isEmpty()) return emptyList()
        return listOf(ShowdownTeam(UUID.randomUUID().toString(), fallbackName, fallbackFormat, ShowdownTeamCodec.pack(sets)))
    }

    private fun parsePackedLine(line: String): ShowdownTeam? {
        val pipe = line.indexOf('|')
        val closeBracket = line.indexOf(']')
        if (pipe <= 0 || closeBracket <= 0 || closeBracket > pipe) return null
        val format = line.substring(0, closeBracket).trim().ifBlank { "gen9" }
        val path = line.substring(closeBracket + 1, pipe).trim()
        val folder = path.substringBeforeLast('/', "").trim()
        val name = path.substringAfterLast('/').trim().ifBlank { "Imported team" }
        val packed = line.substring(pipe + 1).trim()
        if (!isValidPackedTeam(packed)) return null
        return ShowdownTeam(UUID.randomUUID().toString(), name, format, packed, folder = folder)
    }

    private fun looksLikePackedBackupLine(line: String): Boolean {
        val pipe = line.indexOf('|')
        val closeBracket = line.indexOf(']')
        return closeBracket > 0 && pipe > closeBracket
    }

    private fun isValidPackedTeam(packed: String): Boolean = packed.split(']').all { set ->
        val fields = set.split('|')
        fields.size >= 5 && (fields.getOrNull(0).orEmpty().isNotBlank() || fields.getOrNull(1).orEmpty().isNotBlank())
    }

    private fun normalizeMetadata(team: ShowdownTeam): TeamMetadata {
        val folder = team.folder.trim().trim('/')
        val name = team.name.trim().ifBlank { "Untitled team" }
        return TeamMetadata(
            format = team.format.trim().ifBlank { "gen9" },
            path = listOf(folder, name).filter(String::isNotBlank).joinToString("/")
        )
    }

    private data class TeamMetadata(val format: String, val path: String)

    private fun parseReadable(input: String): List<ShowdownTeam> {
        val teams = mutableListOf<ShowdownTeam>()
        var header: TeamMetadata? = null
        val body = mutableListOf<String>()
        fun flush() {
            val current = header ?: return
            val sets = ShowdownTeamCodec.parse(body.joinToString("\n").trim())
            if (sets.isNotEmpty()) {
                teams += ShowdownTeam(
                    UUID.randomUUID().toString(),
                    current.path.substringAfterLast('/'),
                    current.format,
                    ShowdownTeamCodec.pack(sets),
                    folder = current.path.substringBeforeLast('/', "")
                )
            }
            body.clear()
        }
        input.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.startsWith("===") && trimmed.endsWith("===")) {
                flush()
                header = parseReadableHeader(trimmed)
            } else if (header != null) {
                body += line
            }
        }
        flush()
        return teams
    }

    private fun parseReadableHeader(line: String): TeamMetadata {
        val value = line.removePrefix("===").removeSuffix("===").trim()
        val closeBracket = value.indexOf(']')
        if (!value.startsWith("[") || closeBracket <= 1) return TeamMetadata("gen9", value)
        val format = value.substring(1, closeBracket).trim().ifBlank { "gen9" }
        val path = value.substring(closeBracket + 1).trim().ifBlank { "Imported team" }
        return TeamMetadata(format, path)
    }
}
