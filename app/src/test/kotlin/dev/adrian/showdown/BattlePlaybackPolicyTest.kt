package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlaybackPolicyTest {
    @Test
    fun keepsTheResourceSafePlaybackAtTheThorDebugMemoryClass() {
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = true,
                memoryClassMb = 256,
                totalMemoryBytes = 1_073_741_824L,
                availableMemoryBytes = 268_435_456L
            )
        )
    }

    @Test
    fun usesLightweightPlaybackForConstrainedProfiles() {
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = true,
                memoryClassMb = 192,
                totalMemoryBytes = 4_294_967_296L,
                availableMemoryBytes = 1_073_741_824L
            )
        )
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = false,
                memoryClassMb = 192,
                totalMemoryBytes = 4_294_967_296L,
                availableMemoryBytes = 1_073_741_824L
            )
        )
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = true,
                memoryClassMb = 512,
                totalMemoryBytes = 4_294_967_296L,
                availableMemoryBytes = 1_073_741_824L
            )
        )
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = false,
                memoryClassMb = 512,
                totalMemoryBytes = 1_073_741_824L,
                availableMemoryBytes = 1_073_741_824L
            )
        )
        assertTrue(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = false,
                memoryClassMb = 512,
                totalMemoryBytes = 4_294_967_296L,
                availableMemoryBytes = 268_435_456L
            )
        )
    }

    @Test
    fun keepsNativeAnimationsOnNormalDevices() {
        assertFalse(
            shouldUseLightweightBattlePlayback(
                isLowRamDevice = false,
                memoryClassMb = 512,
                totalMemoryBytes = 4_294_967_296L,
                availableMemoryBytes = 1_073_741_824L
            )
        )
    }
}
