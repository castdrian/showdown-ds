package dev.adrian.showdown

private const val LIGHTWEIGHT_PLAYBACK_AVAILABLE_MEMORY_CUTOFF_BYTES = 512L * 1024L * 1024L

internal fun shouldUseLightweightBattlePlayback(
    isLowRamDevice: Boolean,
    availableMemoryBytes: Long
): Boolean =
    isLowRamDevice ||
        availableMemoryBytes in 0 until LIGHTWEIGHT_PLAYBACK_AVAILABLE_MEMORY_CUTOFF_BYTES
