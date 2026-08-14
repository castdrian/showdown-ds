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
    fun anonymousParticipantsMustNotSeeAStaleBattleAfterProcessDeath() {
        assertEquals(
            ShowdownBattleRecovery.Mode.UNRESTORABLE_GUEST,
            ShowdownBattleRecovery.mode("battle-test", false, true)
        )
    }

    @Test
    fun spectatorsCanReopenAByRoomBattleWithoutParticipantIdentity() {
        assertEquals(
            ShowdownBattleRecovery.Mode.SPECTATOR,
            ShowdownBattleRecovery.mode("battle-test", false, false)
        )
    }
}
