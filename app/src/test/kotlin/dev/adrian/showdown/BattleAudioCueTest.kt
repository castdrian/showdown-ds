package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BattleAudioCueTest {
    @Test
    fun mapsBattleResultsToFixedCues() {
        assertEquals(BattleAudioCue.SUPER_EFFECTIVE, BattleAudioCueResolver.cueForProtocolLine("|-supereffective|p1a: Pikachu"))
        assertEquals(BattleAudioCue.NOT_VERY_EFFECTIVE, BattleAudioCueResolver.cueForProtocolLine("|-resisted|p2a: Garchomp"))
        assertEquals(BattleAudioCue.STAT_BOOST, BattleAudioCueResolver.cueForProtocolLine("|-boost|p1a: Pikachu|atk|2"))
        assertEquals(BattleAudioCue.STAT_DROP, BattleAudioCueResolver.cueForProtocolLine("|-unboost|p2a: Garchomp|def|1"))
        assertEquals(BattleAudioCue.STAT_BOOST, BattleAudioCueResolver.cueForProtocolLine("|-setboost|p1a: Pikachu|atk|2"))
        assertEquals(BattleAudioCue.STAT_DROP, BattleAudioCueResolver.cueForProtocolLine("|-setboost|p2a: Garchomp|def|-1"))
        assertEquals(BattleAudioCue.GENERIC_DAMAGE, BattleAudioCueResolver.cueForNativeValue("generic_damage"))
        assertEquals(BattleAudioCue.SUPER_EFFECTIVE, BattleAudioCueResolver.cueForNativeValue("super_effective"))
    }

    @Test
    fun ignoresProtocolLinesWithoutAnAudioCue() {
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|-damage|p1a: Pikachu|100/100"))
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|move|p1a: Pikachu|Tackle|p2a: Garchomp"))
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|-setboost|p1a: Pikachu|atk|0"))
    }
}
