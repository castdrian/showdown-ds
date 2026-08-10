package dev.adrian.showdown

object ShowdownTeamValidation {
    fun setTeamCommand(packed: String) = "/utm ${packed.trim()}"

    fun validateCommand(formatId: String) = "/vtm ${formatId.trim()}"

    fun response(lines: List<String>): String? = lines
        .firstOrNull { it.startsWith("|popup|") }
        ?.let(ShowdownAuthentication::serverError)
}
