package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Test

class MoveAudioWindowTest {
    @Test
    fun cutsARepeatingSoundAtTheEndOfTheOfficialAnimation() {
        assertEquals(MoveAudioWindow.Plan(loop = false, durationMillis = 400L), MoveAudioWindow.plan(1_100L, 400L))
    }

    @Test
    fun loopsOnlyAClipThatEndsBeforeTheOfficialAnimation() {
        assertEquals(MoveAudioWindow.Plan(loop = true, durationMillis = 1_200L), MoveAudioWindow.plan(300L, 1_200L))
    }
}
