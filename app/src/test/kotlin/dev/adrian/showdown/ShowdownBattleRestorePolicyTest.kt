package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownBattleRestorePolicyTest {
    private val persistedRoom = "battle-gen9randombattle-2"

    @Test
    fun ShowdownBattleRecovery_DebuggableBuildWithPersistedRoom_DoesNotRestoreIt() {
        val restored = ShowdownBattleRecovery.restoredActiveRoomId(persistedRoom, debuggableBuild = true)

        assertEquals(null, restored)
    }

    @Test
    fun ShowdownBattleRecovery_ReleaseBuildWithPersistedRoom_RestoresIt() {
        val restored = ShowdownBattleRecovery.restoredActiveRoomId(persistedRoom, debuggableBuild = false)

        assertEquals(persistedRoom, restored)
    }

    @Test
    fun ShowdownBattleRecovery_NoPersistedRoom_RestoresNothing() {
        val restored = ShowdownBattleRecovery.restoredActiveRoomId(null, debuggableBuild = false)

        assertEquals(null, restored)
    }
}
