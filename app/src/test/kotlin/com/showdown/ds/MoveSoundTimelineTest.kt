package com.showdown.ds

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MoveSoundTimelineTest {
    @Test
    fun discardsACompletedLoadForAnEarlierMove() {
        val timeline = MoveSoundTimeline()
        val firstMove = timeline.beginMove()
        val secondMove = timeline.beginMove()

        assertFalse(timeline.isCurrent(firstMove))
        assertTrue(timeline.isCurrent(secondMove))
    }

    @Test
    fun rejectsMoveAudioThatMissedTheAnimationWindow() {
        val timeline = MoveSoundTimeline()
        val move = timeline.beginMove(1_000L)

        assertTrue(timeline.isPlayable(move, 1_750L))
        assertFalse(timeline.isPlayable(move, 1_751L))
    }
}
