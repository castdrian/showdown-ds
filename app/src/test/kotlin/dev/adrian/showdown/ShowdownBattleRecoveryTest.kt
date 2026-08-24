package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownBattleRecoveryTest {
    @Test
    fun noSavedRoomHasNoRecoveryMode() {
        assertEquals(ShowdownBattleRecovery.Mode.NONE, ShowdownBattleRecovery.mode(null, false, false))
        assertEquals(ShowdownBattleRecovery.Mode.NONE, ShowdownBattleRecovery.mode("", true, true))
    }

    @Test
    fun registeredParticipantsCanRejoinTheirBattle() {
        assertEquals(
            ShowdownBattleRecovery.Mode.REGISTERED_PARTICIPANT,
            ShowdownBattleRecovery.mode("battle-test", true, true)
        )
    }

    @Test
    fun anonymousParticipantsRejoinAsReadOnlySpectatorsAfterProcessDeath() {
        assertEquals(
            ShowdownBattleRecovery.Mode.GUEST_SPECTATOR,
            ShowdownBattleRecovery.mode("battle-test", false, true)
        )
    }

    @Test
    fun persistedSpectatorRecoveryRemainsReadOnly() {
        assertEquals(
            ShowdownBattleRecovery.Mode.SPECTATOR,
            ShowdownBattleRecovery.mode("battle-test", false, true, true)
        )
    }

    @Test
    fun spectatorsCanReopenAByRoomBattleWithoutParticipantIdentity() {
        assertEquals(
            ShowdownBattleRecovery.Mode.SPECTATOR,
            ShowdownBattleRecovery.mode("battle-test", false, false)
        )
    }

    @Test
    fun guestSpectatorRecoveryDropsAStalePendingDecision() {
        assertEquals(
            null,
            ShowdownBattleRecovery.pendingDecisionCommand(
                ShowdownBattleRecovery.Mode.GUEST_SPECTATOR,
                "/choose move 1"
            )
        )
    }

    @Test
    fun participantRecoveryPreservesAPendingDecision() {
        assertEquals(
            "/choose move 1",
            ShowdownBattleRecovery.pendingDecisionCommand(
                ShowdownBattleRecovery.Mode.REGISTERED_PARTICIPANT,
                "/choose move 1"
            )
        )
    }
}
