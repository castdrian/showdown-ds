package dev.adrian.showdown

private const val LIGHTWEIGHT_PLAYBACK_MEMORY_CLASS_CUTOFF_MB = 256
private const val LIGHTWEIGHT_PLAYBACK_TOTAL_MEMORY_CUTOFF_BYTES = 2L * 1024L * 1024L * 1024L
private const val LIGHTWEIGHT_PLAYBACK_AVAILABLE_MEMORY_CUTOFF_BYTES = 512L * 1024L * 1024L

internal fun shouldUseLightweightBattlePlayback(
    isLowRamDevice: Boolean,
    memoryClassMb: Int,
    totalMemoryBytes: Long,
    availableMemoryBytes: Long
): Boolean =
    isLowRamDevice ||
        memoryClassMb in 1..LIGHTWEIGHT_PLAYBACK_MEMORY_CLASS_CUTOFF_MB ||
        totalMemoryBytes in 0 until LIGHTWEIGHT_PLAYBACK_TOTAL_MEMORY_CUTOFF_BYTES ||
        availableMemoryBytes in 0 until LIGHTWEIGHT_PLAYBACK_AVAILABLE_MEMORY_CUTOFF_BYTES
