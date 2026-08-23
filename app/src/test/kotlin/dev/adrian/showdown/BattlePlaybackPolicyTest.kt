package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BattlePlaybackPolicyTest {
    @Test
    fun keepsTheResourceSafePlaybackAtTheThorDebugMemoryClass() {
        assertTrue(shouldUseLightweightBattlePlayback(isLowRamDevice = true, memoryClassMb = 256))
    }

    @Test
    fun usesLightweightPlaybackForConstrainedProfiles() {
        assertTrue(shouldUseLightweightBattlePlayback(isLowRamDevice = true, memoryClassMb = 192))
        assertTrue(shouldUseLightweightBattlePlayback(isLowRamDevice = false, memoryClassMb = 192))
        assertTrue(shouldUseLightweightBattlePlayback(isLowRamDevice = true, memoryClassMb = 512))
    }

    @Test
    fun keepsNativeAnimationsOnNormalDevices() {
        assertFalse(shouldUseLightweightBattlePlayback(isLowRamDevice = false, memoryClassMb = 512))
    }
}
