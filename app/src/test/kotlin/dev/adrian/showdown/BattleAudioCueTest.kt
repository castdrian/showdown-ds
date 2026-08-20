package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
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
        assertEquals(BattleAudioCue.STAT_DROP, BattleAudioCueResolver.cueForProtocolLine("|-boost|p1a: Pikachu|atk|-1"))
        assertEquals(BattleAudioCue.STAT_BOOST, BattleAudioCueResolver.cueForProtocolLine("|-unboost|p2a: Garchomp|def|-1"))
        assertEquals(
            BattleAudioCue.STAT_BOOST,
            BattleAudioCueResolver.cueForProtocolLine("|-setboost|p1a: Pikachu|atk|2|[from]ability: Contrary")
        )
        assertEquals(BattleAudioCue.STAT_DROP, BattleAudioCueResolver.cueForProtocolLine("|-clearpositiveboost|p1a: Pikachu"))
        assertEquals(BattleAudioCue.STAT_DROP, BattleAudioCueResolver.cueForProtocolLine("|-clearboost|p1a: Pikachu"))
        assertEquals(BattleAudioCue.STAT_DROP, BattleAudioCueResolver.cueForProtocolLine("|-clearallboost"))
        assertEquals(BattleAudioCue.STAT_BOOST, BattleAudioCueResolver.cueForProtocolLine("|-clearnegativeboost|p1a: Pikachu"))
        assertEquals(BattleAudioCue.STAT_BOOST, BattleAudioCueResolver.cueForProtocolLine("|-restoreboost|p1a: Pikachu"))
        assertEquals(BattleAudioCue.GENERIC_DAMAGE, BattleAudioCueResolver.cueForNativeValue("generic_damage"))
        assertEquals(BattleAudioCue.SUPER_EFFECTIVE, BattleAudioCueResolver.cueForNativeValue("super_effective"))
    }

    @Test
    fun ignoresProtocolLinesWithoutAnAudioCue() {
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|-damage|p1a: Pikachu|100/100"))
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|move|p1a: Pikachu|Tackle|p2a: Garchomp"))
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|-boost|p1a: Pikachu|atk|0"))
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|-unboost|p2a: Garchomp|def|0|[from]ability: Contrary"))
        assertNull(BattleAudioCueResolver.cueForProtocolLine("|-setboost|p1a: Pikachu|atk|0"))
    }

    @Test
    fun previewHoldsCoverTheLongestCueBeforeTheNextSample() {
        assertEquals(2_600L, BattleAudioCue.STAT_DROP.previewHoldMillis)
        assertTrue(BattleAudioCue.values().all { it.previewHoldMillis >= 800L })
        assertEquals(
            listOf(0L, 1_000L, 3_200L, 4_000L, 6_400L),
            BattleAudioPreviewTiming.startOffsets(BattleAudioCue.values().toList())
        )
        assertEquals(9_000L, BattleAudioPreviewTiming.completionDelay(BattleAudioCue.values().toList()))
    }
}
