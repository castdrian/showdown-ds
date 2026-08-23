package dev.adrian.showdown

import kotlin.math.roundToLong

object BattleSceneTiming {
    const val faintDurationNanos = 520_000_000L
    const val statusFadeDurationNanos = 300_000_000L
    const val summonBallDurationNanos = 300_000_000L
    const val summonDropDurationNanos = 400_000_000L
    const val summonSettleDurationNanos = 300_000_000L
    const val summonDurationNanos = summonBallDurationNanos + summonDropDurationNanos + summonSettleDurationNanos
    const val lightweightMoveDurationNanos = 1_200_000_000L
    const val lightweightImpactDelayNanos = 650_000_000L
    const val lightweightImpactDurationNanos = 700_000_000L
    const val lightweightStatDurationNanos = 1_100_000_000L

    fun faintProgress(pokemon: String, condition: String, latestFaintedPokemon: String, faintAtNanos: Long, nowNanos: Long): Float {
        if (!condition.contains("FNT", true)) return 0f
        if (!pokemon.equals(latestFaintedPokemon, true)) return 1f
        return ((nowNanos - faintAtNanos).toFloat() / faintDurationNanos).coerceIn(0f, 1f)
    }

    fun statusCardAlpha(pokemon: String, condition: String, latestFaintedPokemon: String, faintAtNanos: Long, nowNanos: Long): Float {
        if (!condition.contains("FNT", true)) return 1f
        return 1f
    }

    fun summonProgress(summonAtNanos: Long, nowNanos: Long) =
        if (summonAtNanos <= 0L) 1f else ((nowNanos - summonAtNanos).toFloat() / summonDurationNanos).coerceIn(0f, 1f)

    fun summonSpriteAlpha(summonAtNanos: Long, nowNanos: Long): Float {
        val elapsed = (nowNanos - summonAtNanos).coerceAtLeast(0L)
        return ((elapsed - summonBallDurationNanos).toFloat() / summonDropDurationNanos).coerceIn(0f, 1f)
    }

    fun summonSpriteScale(summonAtNanos: Long, nowNanos: Long) =
        0.12f + summonSpriteAlpha(summonAtNanos, nowNanos) * 0.88f

    fun summonVerticalOffset(summonAtNanos: Long, nowNanos: Long): Float {
        val elapsed = (nowNanos - summonAtNanos).coerceAtLeast(0L)
        val dropEnd = summonBallDurationNanos + summonDropDurationNanos
        return when {
            elapsed < summonBallDurationNanos -> -10f
            elapsed < dropEnd -> -10f + 40f * ((elapsed - summonBallDurationNanos).toFloat() / summonDropDurationNanos)
            else -> 30f * (1f - ((elapsed - dropEnd).toFloat() / summonSettleDurationNanos).coerceIn(0f, 1f))
        }
    }

    fun summonBallAlpha(summonAtNanos: Long, nowNanos: Long): Float {
        val elapsed = (nowNanos - summonAtNanos).coerceAtLeast(0L)
        return when {
            elapsed >= summonBallDurationNanos * 2 -> 0f
            elapsed < summonBallDurationNanos -> (elapsed.toFloat() / summonBallDurationNanos).coerceIn(0f, 1f)
            else -> (1f - (elapsed - summonBallDurationNanos).toFloat() / summonBallDurationNanos).coerceIn(0f, 1f)
        }
    }

    fun summonStatusCardAlpha(summonAtNanos: Long, nowNanos: Long): Float {
        val elapsed = (nowNanos - summonAtNanos).coerceAtLeast(0L)
        return ((elapsed - summonBallDurationNanos).toFloat() / summonDropDurationNanos).coerceIn(0f, 1f)
    }

    fun scaledDurationNanos(durationNanos: Long, speed: Float): Long =
        (durationNanos.toDouble() / BattlePlaybackSpeed.coerce(speed).toDouble()).roundToLong().coerceAtLeast(1L)
}
