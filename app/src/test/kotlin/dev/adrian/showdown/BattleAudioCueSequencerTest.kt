package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class BattleAudioCueSequencerTest {
    @Test
    fun effectivenessCueWaitsForDamageCue() {
        val emitted = mutableListOf<BattleAudioCue>()
        val sequencer = BattleAudioCueSequencer(emitted::add)

        sequencer.beginMove()
        sequencer.receive(BattleAudioCue.SUPER_EFFECTIVE)
        assertEquals(emptyList<BattleAudioCue>(), emitted)

        sequencer.receive(BattleAudioCue.GENERIC_DAMAGE)

        assertEquals(
            listOf(BattleAudioCue.GENERIC_DAMAGE, BattleAudioCue.SUPER_EFFECTIVE),
            emitted
        )
    }

    @Test
    fun statusCuesPlayImmediatelyAndDamagePlaysOncePerMove() {
        val emitted = mutableListOf<BattleAudioCue>()
        val sequencer = BattleAudioCueSequencer(emitted::add)

        sequencer.beginMove()
        sequencer.receive(BattleAudioCue.STAT_BOOST)
        sequencer.receive(BattleAudioCue.GENERIC_DAMAGE)
        sequencer.receive(BattleAudioCue.GENERIC_DAMAGE)

        assertEquals(
            listOf(BattleAudioCue.STAT_BOOST, BattleAudioCue.GENERIC_DAMAGE),
            emitted
        )
    }

    @Test
    fun pendingEffectivenessDoesNotLeakIntoTheNextMove() {
        val emitted = mutableListOf<BattleAudioCue>()
        val sequencer = BattleAudioCueSequencer(emitted::add)

        sequencer.beginMove()
        sequencer.receive(BattleAudioCue.NOT_VERY_EFFECTIVE)
        sequencer.beginMove()
        sequencer.receive(BattleAudioCue.GENERIC_DAMAGE)

        assertEquals(listOf(BattleAudioCue.GENERIC_DAMAGE), emitted)
    }

    @Test
    fun battleResetDiscardsPendingCues() {
        val emitted = mutableListOf<BattleAudioCue>()
        val sequencer = BattleAudioCueSequencer(emitted::add)

        sequencer.beginMove()
        sequencer.receive(BattleAudioCue.SUPER_EFFECTIVE)
        sequencer.reset()
        sequencer.receive(BattleAudioCue.GENERIC_DAMAGE)

        assertEquals(listOf(BattleAudioCue.GENERIC_DAMAGE), emitted)
    }
}
