package dev.adrian.showdown

object ShowdownTeamImportRefresh {
    fun shouldRefresh(libraryVisible: Boolean, returnToLibrary: Boolean, imported: List<ShowdownTeam>) =
        (libraryVisible || returnToLibrary) && imported.isNotEmpty()
}
