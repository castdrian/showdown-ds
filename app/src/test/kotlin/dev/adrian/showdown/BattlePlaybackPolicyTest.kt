package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlaybackPolicyTest {
    @Test
    fun keepsLightweightPlaybackOnAndroidLowRamDevices() {
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = true,
                availableMemoryBytes = 1_073_741_824L
            )
        )
    }

    @Test
    fun keepsLightweightPlaybackWhenAvailableMemoryIsBelowTheCutoff() {
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = false,
                availableMemoryBytes = 536_870_911L
            )
        )
    }

    @Test
    fun keepsOfficialAnimationsOnTheRestrictedThorDebugProfileWhenMemoryIsAvailable() {
        assertFalse(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = false,
                availableMemoryBytes = 663_748_608L
            )
        )
    }

    @Test
    fun keepsOfficialAnimationsOnNormalDevices() {
        assertFalse(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = false,
                availableMemoryBytes = 1_073_741_824L
            )
        )
    }
}
