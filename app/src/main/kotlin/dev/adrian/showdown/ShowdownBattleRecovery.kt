package dev.adrian.showdown

object ShowdownBattleRecovery {
    enum class Mode {
        NONE,
        REGISTERED_PARTICIPANT,
        SPECTATOR,
        UNRESTORABLE_GUEST
    }

    fun mode(activeRoomId: String?, registered: Boolean, participant: Boolean): Mode = when {
        activeRoomId.isNullOrBlank() -> Mode.NONE
        participant && registered -> Mode.REGISTERED_PARTICIPANT
        participant -> Mode.UNRESTORABLE_GUEST
        else -> Mode.SPECTATOR
    }
}
