package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleSceneTimingTest {
    @Test
    fun faintedStatusCardRemainsVisibleAfterTheSpriteLeavesTheField() {
        val faintAtNanos = 2_000_000_000L

        assertEquals(1f, BattleSceneTiming.statusCardAlpha("Tapu Koko", "FNT", "Tapu Koko", faintAtNanos, faintAtNanos), 0.001f)
        assertEquals(1f, BattleSceneTiming.statusCardAlpha("Tapu Koko", "FNT", "Tapu Koko", faintAtNanos, faintAtNanos + BattleSceneTiming.statusFadeDurationNanos), 0.001f)
        assertEquals(1f, BattleSceneTiming.statusCardAlpha("Tapu Koko", "FNT", "Tapu Koko", faintAtNanos, faintAtNanos + BattleSceneTiming.statusFadeDurationNanos * 4), 0.001f)
    }

    @Test
    fun faintedSpriteCompletesAfterItsStatusCardHasFaded() {
        val faintAtNanos = 2_000_000_000L

        assertEquals(0f, BattleSceneTiming.faintProgress("Tapu Koko", "FNT", "Tapu Koko", faintAtNanos, faintAtNanos), 0.001f)
        assertEquals(1f, BattleSceneTiming.faintProgress("Tapu Koko", "FNT", "Tapu Koko", faintAtNanos, faintAtNanos + BattleSceneTiming.faintDurationNanos), 0.001f)
    }

    @Test
    fun summonRevealsTheCombatantAfterThePokeballAndSettlesBeforeTheNextEntrance() {
        val summonAtNanos = 2_000_000_000L

        assertEquals(0f, BattleSceneTiming.summonSpriteAlpha(summonAtNanos, summonAtNanos), 0.001f)
        assertEquals(1f, BattleSceneTiming.summonBallAlpha(summonAtNanos, summonAtNanos + BattleSceneTiming.summonBallDurationNanos), 0.001f)
        assertEquals(1f, BattleSceneTiming.summonSpriteAlpha(summonAtNanos, summonAtNanos + BattleSceneTiming.summonBallDurationNanos + BattleSceneTiming.summonDropDurationNanos), 0.001f)
        assertEquals(1f, BattleSceneTiming.summonProgress(summonAtNanos, summonAtNanos + BattleSceneTiming.summonDurationNanos), 0.001f)
    }

    @Test
    fun lightweightImpactTimingSlowsWithHumanPlaybackSpeed() {
        assertEquals(
            BattleSceneTiming.lightweightImpactDelayNanos,
            BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightImpactDelayNanos, 1f)
        )
        assertEquals(
            866_666_667L,
            BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightImpactDelayNanos, 0.75f)
        )
    }
}
