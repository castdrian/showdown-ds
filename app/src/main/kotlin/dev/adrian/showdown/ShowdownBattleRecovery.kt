package dev.adrian.showdown

object ShowdownBattleRecovery {
    enum class Mode {
        NONE,
        REGISTERED_PARTICIPANT,
        SPECTATOR,
        GUEST_SPECTATOR
    }

    fun mode(activeRoomId: String?, registered: Boolean, participant: Boolean, spectator: Boolean = false): Mode = when {
        activeRoomId.isNullOrBlank() -> Mode.NONE
        spectator -> Mode.SPECTATOR
        participant && registered -> Mode.REGISTERED_PARTICIPANT
        participant -> Mode.GUEST_SPECTATOR
        else -> Mode.SPECTATOR
    }

    fun pendingDecisionCommand(mode: Mode, command: String?): String? = command.takeUnless {
        mode == Mode.GUEST_SPECTATOR
    }
}
