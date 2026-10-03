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

        assertEquals(0f, BattleSceneTiming.faintProgress("p1a", "FNT", "p1a", faintAtNanos, faintAtNanos), 0.001f)
        assertEquals(1f, BattleSceneTiming.faintProgress("p1a", "FNT", "p1a", faintAtNanos, faintAtNanos + BattleSceneTiming.faintDurationNanos), 0.001f)
    }

    @Test
    fun aLaterFaintOnAnotherSlotDoesNotRestartAnEarlierSpriteFade() {
        val laterFaintAtNanos = 2_520_000_000L

        assertEquals(
            1f,
            BattleSceneTiming.faintProgress("p1a", "FNT", "p2a", laterFaintAtNanos, laterFaintAtNanos),
            0.001f
        )
        assertEquals(
            0f,
            BattleSceneTiming.faintProgress("p2a", "FNT", "p2a", laterFaintAtNanos, laterFaintAtNanos),
            0.001f
        )
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
    fun lightweightImpactBeginsWhenTheMoveReachesItsTarget() {
        assertEquals(
            BattleSceneTiming.lightweightMoveDurationNanos,
            BattleSceneTiming.lightweightImpactDelayNanos
        )
        assertEquals(
            BattleSceneTiming.lightweightImpactDelayNanos,
            BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightImpactDelayNanos, 1f)
        )
        assertEquals(
            BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightMoveDurationNanos, 0.75f),
            BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightImpactDelayNanos, 0.75f)
        )
    }

    @Test
    fun lightweightDamageWithoutAnAnimationCanCueImmediately() {
        assertEquals(0L, BattleSceneTiming.lightweightImpactDelayForAnimation(false, 0.75f))
        assertEquals(
            BattleSceneTiming.scaledDurationNanos(BattleSceneTiming.lightweightMoveDurationNanos, 0.75f),
            BattleSceneTiming.lightweightImpactDelayForAnimation(true, 0.75f)
        )
    }
}
