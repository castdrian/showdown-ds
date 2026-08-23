package dev.adrian.showdown

import java.util.Locale

enum class BattleClockUrgency {
    NORMAL,
    WARNING,
    CRITICAL
}

object BattleClockPresentation {
    fun timeLabel(seconds: Int): String {
        val safeSeconds = seconds.coerceAtLeast(0)
        return String.format(Locale.ROOT, "%d:%02d", safeSeconds / 60, safeSeconds % 60)
    }

    fun urgency(seconds: Int): BattleClockUrgency = when {
        seconds <= 10 -> BattleClockUrgency.CRITICAL
        seconds <= 30 -> BattleClockUrgency.WARNING
        else -> BattleClockUrgency.NORMAL
    }
}
