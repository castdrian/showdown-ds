package dev.adrian.showdown

object ShowdownTeamEditorDefaults {
    fun format(
        initialFormat: String?,
        current: BattleSession.MatchFormat,
        available: Collection<BattleSession.MatchFormat>
    ): String {
        initialFormat?.trim()?.takeIf(String::isNotBlank)?.let { return it }
        if (!BattleSession.MatchFormat.usesRandomTeams(current)) {
            return current.id.trim().takeIf(String::isNotBlank) ?: "gen9ou"
        }
        return available.firstOrNull {
            it.canChallenge && !BattleSession.MatchFormat.usesRandomTeams(it) && it.id.isNotBlank()
        }?.id?.trim() ?: "gen9ou"
    }
}
