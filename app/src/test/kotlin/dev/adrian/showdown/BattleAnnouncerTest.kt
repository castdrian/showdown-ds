package dev.adrian.showdown

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleAnnouncerTest {
    @Test
    fun protocolEventsResolveToTheRelevantAnnouncerCues() {
        assertEquals(BattleAnnouncerCue.BATTLE_START, BattleAnnouncerCueResolver.cueForProtocolLine("|init|battle"))
        assertEquals(BattleAnnouncerCue.SWITCH, BattleAnnouncerCueResolver.cueForProtocolLine("|switch|p1a: Pikachu|Pikachu"))
        assertEquals(BattleAnnouncerCue.MOVE, BattleAnnouncerCueResolver.cueForProtocolLine("|move|p1a: Pikachu|Thunderbolt|p2a: Gengar"))
        assertEquals(BattleAnnouncerCue.HIT, BattleAnnouncerCueResolver.cueForProtocolLine("|-damage|p2a: Gengar|0 fnt"))
        assertEquals(BattleAnnouncerCue.BURN, BattleAnnouncerCueResolver.cueForProtocolLine("|-status|p2a: Gengar|brn"))
        assertEquals(BattleAnnouncerCue.TOXIC_POISON, BattleAnnouncerCueResolver.cueForProtocolLine("|-status|p2a: Gengar|tox"))
        assertEquals(BattleAnnouncerCue.SANDSTORM, BattleAnnouncerCueResolver.cueForProtocolLine("|-weather|Sandstorm|[from] ability: Sand Stream"))
        assertEquals(BattleAnnouncerCue.HAIL, BattleAnnouncerCueResolver.cueForProtocolLine("|-weather|Snow|[from] ability: Snow Warning"))
        assertEquals(BattleAnnouncerCue.HEAL, BattleAnnouncerCueResolver.cueForProtocolLine("|-heal|p1a: Pikachu|100/100"))
        assertNull(BattleAnnouncerCueResolver.cueForProtocolLine("|-item|p1a: Pikachu|Leftovers"))
        assertEquals(BattleAnnouncerCue.BATTLE_END, BattleAnnouncerCueResolver.cueForProtocolLine("|win|ADRIAN"))
    }

    @Test
    fun animationBridgeValuesResolveToTheRelevantAnnouncerCues() {
        assertEquals(BattleAnnouncerCue.BATTLE_START, BattleAnnouncerCueResolver.cueForNativeValue("battle_start"))
        assertEquals(BattleAnnouncerCue.MOVE, BattleAnnouncerCueResolver.cueForNativeValue("move"))
        assertEquals(BattleAnnouncerCue.HIT, BattleAnnouncerCueResolver.cueForNativeValue("hit"))
        assertEquals(BattleAnnouncerCue.MULTI_HIT, BattleAnnouncerCueResolver.cueForNativeValue("multi_hit"))
        assertEquals(BattleAnnouncerCue.BURN, BattleAnnouncerCueResolver.cueForNativeValue("burn"))
        assertEquals(BattleAnnouncerCue.SANDSTORM, BattleAnnouncerCueResolver.cueForNativeValue("sandstorm"))
        assertEquals(BattleAnnouncerCue.PARALYSIS, BattleAnnouncerCueResolver.cueForNativeValue("paralysis"))
        assertEquals(BattleAnnouncerCue.SLEEP, BattleAnnouncerCueResolver.cueForNativeValue("sleep"))
        assertEquals(BattleAnnouncerCue.FREEZE, BattleAnnouncerCueResolver.cueForNativeValue("freeze"))
        assertEquals(BattleAnnouncerCue.CONFUSION, BattleAnnouncerCueResolver.cueForNativeValue("confusion"))
        assertEquals(BattleAnnouncerCue.STAT_BOOST, BattleAnnouncerCueResolver.cueForNativeValue("stat_boost"))
        assertEquals(BattleAnnouncerCue.STAT_DROP, BattleAnnouncerCueResolver.cueForNativeValue("stat_drop"))
        assertNull(BattleAnnouncerCueResolver.cueForNativeValue("item"))
        assertNull(BattleAnnouncerCueResolver.cueForNativeValue("generic_damage"))
    }

    @Test
    fun protocolEventsResolveToCommonStatusAndStatAnnouncerCues() {
        assertEquals(
            BattleAnnouncerCue.PARALYSIS,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-status|p2a: Gengar|par")
        )
        assertEquals(
            BattleAnnouncerCue.SLEEP,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-status|p2a: Gengar|slp")
        )
        assertEquals(
            BattleAnnouncerCue.FREEZE,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-status|p2a: Gengar|frz")
        )
        assertEquals(
            BattleAnnouncerCue.CONFUSION,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-start|p2a: Gengar|confusion")
        )
        assertEquals(
            BattleAnnouncerCue.STAT_BOOST,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-boost|p1a: Pikachu|spa|2")
        )
        assertEquals(
            BattleAnnouncerCue.STAT_DROP,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-unboost|p1a: Pikachu|spe|1")
        )
        assertEquals(
            BattleAnnouncerCue.STAT_BOOST,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-setboost|p1a: Pikachu|spa|2")
        )
        assertEquals(
            BattleAnnouncerCue.STAT_DROP,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-clearpositiveboost|p1a: Pikachu")
        )
        assertEquals(
            BattleAnnouncerCue.STAT_BOOST,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-restoreboost|p1a: Pikachu")
        )
        assertNull(
            BattleAnnouncerCueResolver.cueForProtocolLine("|-status|p2a: Gengar|tox|[silent]")
        )
        assertNull(
            BattleAnnouncerCueResolver.cueForProtocolLine("|-boost|p1a: Pikachu|atk|0")
        )
    }

    @Test
    fun statEventsResolveWhileChatRemainsSilent() {
        assertEquals(
            BattleAnnouncerCue.STAT_BOOST,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-boost|p1a: Pikachu|atk|1")
        )
        assertNull(BattleAnnouncerCueResolver.cueForProtocolLine("|chat|ADRIAN|Good luck!"))
        assertEquals(
            listOf(BattleAnnouncerCue.MOVE, BattleAnnouncerCue.HIT),
            BattleAnnouncerCueResolver.cuesForProtocol(
                listOf(
                    "|move|p1a: Pikachu|Thunderbolt|p2a: Gengar",
                    "|-damage|p2a: Gengar|40/100"
                )
            )
        )
    }

    @Test
    fun residualDamageDoesNotAnnounceAMoveImpact() {
        assertNull(BattleAnnouncerCueResolver.cueForProtocolLine("|-damage|p1a: Pikachu|90/100|[from] brn"))
        assertNull(BattleAnnouncerCueResolver.cueForProtocolLine("|-damage|p2a: Garchomp|90/100|[from] ability: Rough Skin"))
        assertEquals(
            BattleAnnouncerCue.HIT,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-damage|p2a: Garchomp|40/100|[from] move: Stealth Rock")
        )
        assertEquals(
            BattleAnnouncerCue.HIT,
            BattleAnnouncerCueResolver.cueForProtocolLine("|-sethp|p1a: Pikachu|60/100|p2a: Garchomp|40/100|[from] move: Pain Split")
        )
        assertNull(BattleAnnouncerCueResolver.cueForProtocolLine("|-sethp|p1a: Pikachu|60/100|p2a: Garchomp|40/100"))
        assertEquals(BattleAnnouncerCue.HIT, BattleAnnouncerCueResolver.cueForProtocolLine("|-damage|p2a: Garchomp|90/100"))
    }

    @Test
    fun filteredProtocolCuesStaySilentForHealingAndUnresolvedResidualDamage() {
        assertEquals(
            listOf(BattleAnnouncerCue.MOVE),
            BattleAnnouncerCueResolver.cuesForProtocol(
                listOf(
                    "|move|p1a: Pikachu|Pain Split|p2a: Garchomp",
                    "|-sethp|p1a: Pikachu|100/100|p2a: Garchomp|100/100|[from] move: Pain Split"
                ),
                emptySet()
            )
        )
        assertEquals(
            listOf(BattleAnnouncerCue.MOVE, BattleAnnouncerCue.HIT),
            BattleAnnouncerCueResolver.cuesForProtocol(
                listOf(
                    "|move|p1a: Pikachu|Thunderbolt|p2a: Garchomp",
                    "|-damage|p2a: Garchomp|40/100"
                ),
                setOf(1)
            )
        )
    }

    @Test
    fun multiHitPacketsAnnounceTheHitCountInsteadOfEveryIndividualImpact() {
        assertEquals(
            listOf(BattleAnnouncerCue.MOVE, BattleAnnouncerCue.MULTI_HIT),
            BattleAnnouncerCueResolver.cuesForProtocol(
                listOf(
                    "|move|p1a: Pikachu|Bullet Seed|p2a: Garchomp",
                    "|-damage|p2a: Garchomp|80/100",
                    "|-damage|p2a: Garchomp|60/100",
                    "|-hitcount|p2a: Garchomp|2"
                )
            )
        )
    }

    @Test
    fun assetsUseTheSelectedClipFromTheBundledSubset() {
        val path = BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.BATTLE_START)

        assertEquals("announcer/tb_014.wav", path)
        assertEquals("announcer/cb_310.wav", BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.SWITCH))
        assertEquals("announcer/tb_820.wav", BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.POISON))
        assertEquals("announcer/tb_830.wav", BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.TOXIC_POISON))
        assertEquals("announcer/tb_850.wav", BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.BURN))
        assertEquals("announcer/cb_171.wav", BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.HEAL))
        assertEquals("announcer/tb_890.wav", BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.STAT_BOOST))
        assertEquals("announcer/tb_892.wav", BattleAnnouncerAssets.assetPath(BattleAnnouncerCue.STAT_DROP))
        BattleAnnouncerCue.values().forEach { cue ->
            assertTrue(File("src/main/assets/${BattleAnnouncerAssets.assetPath(cue)}").isFile)
        }
    }
}
