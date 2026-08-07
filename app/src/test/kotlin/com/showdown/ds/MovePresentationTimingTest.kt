package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Test

class MovePresentationTimingTest {
    @Test
    fun repeatsShorterVisualsUntilTheMoveSoundIsCovered() {
        assertEquals(MovePresentationTiming.Plan(3, 1_200L), MovePresentationTiming.plan(400L, 1_100L))
    }

    @Test
    fun preservesLongerVisualsAndLetsAudioLoopToMatch() {
        assertEquals(MovePresentationTiming.Plan(1, 1_200L), MovePresentationTiming.plan(1_200L, 300L))
    }

    @Test
    fun skipsPresentationWhenShowdownDoesNotScheduleAnAnimation() {
        assertEquals(MovePresentationTiming.Plan(1, 0L), MovePresentationTiming.plan(0L, 1_000L))
    }

    @Test
    fun coversTheEntireMoveSoundWithOfficialAnimationCycles() {
        assertEquals(MovePresentationTiming.Plan(10, 2_000L), MovePresentationTiming.plan(200L, 2_000L))
    }
}
