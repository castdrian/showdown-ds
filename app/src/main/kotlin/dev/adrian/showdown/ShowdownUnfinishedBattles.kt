package dev.adrian.showdown

/**
 * Tracks battles the server reports as already in progress when an account logs in.
 *
 * Showdown resends every unfinished battle of an account on each login. Rather than reopening them
 * automatically, they are held here (their protocol packets buffered) so the user can choose to
 * resume or forfeit each one. Only rooms present in the first search update after a named login are
 * held; battles that start later (a match, an accepted challenge) are the user's own doing.
 */
class ShowdownUnfinishedBattles {
    /** A held battle as shown to the user. */
    data class Entry(val roomId: String, val format: String, val title: String)

    private class HeldBattle(val format: String) {
        val packets = mutableListOf<List<String>>()
    }

    private val held = linkedMapOf<String, HeldBattle>()
    private var awaitingLoginSnapshot = false

    /** Forgets every held battle and the pending login snapshot; call when the connection restarts. */
    fun onConnectionReset() {
        held.clear()
        awaitingLoginSnapshot = false
    }

    /** Arms the snapshot: the next search update lists the battles that already existed at login. */
    fun onNamedLogin() {
        awaitingLoginSnapshot = true
    }

    /**
     * Applies the server's list of the account's games. Unexpected rooms in the login snapshot become
     * held; held rooms the server no longer reports are dropped.
     */
    fun onSearchUpdate(games: Map<String, String>, expectedRoomIds: Set<String>) {
        held.keys.retainAll(games.keys)
        if (!awaitingLoginSnapshot) return
        awaitingLoginSnapshot = false
        games.filterKeys { it !in expectedRoomIds && it !in held }
            .forEach { (roomId, format) -> held[roomId] = HeldBattle(format.removeSuffix(NO_RENAMES_MARKER)) }
    }

    fun isHeld(roomId: String): Boolean = roomId in held

    fun heldRoomIds(): Set<String> = held.keys.toSet()

    /** Buffers a packet for a held room, dropping the room if the packet ends the battle. */
    fun holdPacket(roomId: String, lines: List<String>) {
        val battle = held[roomId] ?: return
        battle.packets += lines
        if (lines.any(::endsBattle)) held.remove(roomId)
    }

    /** True once something is held and every held battle has told us who is playing. */
    fun isReadyToPresent(): Boolean = held.isNotEmpty() && held.values.all { titleOf(it) != null }

    fun entries(): List<Entry> = held.map { (roomId, battle) ->
        Entry(roomId, battle.format, titleOf(battle) ?: roomId)
    }

    /** Stops holding the room and returns its buffered packets, oldest first, for replay. */
    fun release(roomId: String): List<List<String>> = held.remove(roomId)?.packets.orEmpty()

    private fun titleOf(battle: HeldBattle): String? = battle.packets.asSequence()
        .flatten()
        .firstOrNull { it.startsWith(TITLE_PREFIX) }
        ?.removePrefix(TITLE_PREFIX)

    private fun endsBattle(line: String) = line.startsWith(WIN_PREFIX) || line == TIE_LINE

    private companion object {
        const val NO_RENAMES_MARKER = "*"
        const val TITLE_PREFIX = "|title|"
        const val WIN_PREFIX = "|win|"
        const val TIE_LINE = "|tie|"
    }
}
