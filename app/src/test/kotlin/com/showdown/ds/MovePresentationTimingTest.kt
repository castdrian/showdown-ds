package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Test

class MovePresentationTimingTest {
    @Test
    fun preservesTheOfficialVisualLengthWhenTheSoundIsLonger() {
        assertEquals(400L, MovePresentationTiming.duration(400L))
    }

    @Test
    fun preservesTheOfficialVisualLengthWhenTheSoundIsShorter() {
        assertEquals(1_200L, MovePresentationTiming.duration(1_200L))
    }

    @Test
    fun skipsPresentationWhenShowdownDoesNotScheduleAnAnimation() {
        assertEquals(0L, MovePresentationTiming.duration(0L))
    }

    @Test
    fun neverExtendsAnOfficialAnimationToMatchTheSound() {
        assertEquals(200L, MovePresentationTiming.duration(200L))
    }
}
