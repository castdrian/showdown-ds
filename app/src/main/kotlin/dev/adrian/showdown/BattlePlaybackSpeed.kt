package dev.adrian.showdown

object BattlePlaybackSpeed {
    const val MINIMUM = 0.5f
    const val MAXIMUM = 2f

    fun coerce(value: Float): Float = if (value.isFinite()) value.coerceIn(MINIMUM, MAXIMUM) else 1f
}
