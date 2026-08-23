package dev.adrian.showdown

private const val LIGHTWEIGHT_PLAYBACK_MEMORY_CLASS_CUTOFF_MB = 256

internal fun shouldUseLightweightBattlePlayback(isLowRamDevice: Boolean, memoryClassMb: Int): Boolean =
    isLowRamDevice || memoryClassMb in 1..LIGHTWEIGHT_PLAYBACK_MEMORY_CLASS_CUTOFF_MB
