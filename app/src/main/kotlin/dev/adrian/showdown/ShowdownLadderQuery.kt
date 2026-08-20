package dev.adrian.showdown

object ShowdownLadderQuery {
    fun searchText(rank: Int, entry: ShowdownLobbyState.LadderEntry): String = listOf(
        rank + 1,
        entry.username,
        entry.elo,
        entry.gxe,
        entry.rpr,
        entry.rprd,
        entry.coil ?: ""
    ).joinToString(" ")
}
