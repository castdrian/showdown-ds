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
        assertEquals(BattleAnnouncerCue.MOVE, BattleAnnouncerCueResolver.cueForProtocolLine("|move|p1a: Pikachu|Thunderbolt|p2a: Gengar"))
        assertEquals(BattleAnnouncerCue.HIT, BattleAnnouncerCueResolver.cueForProtocolLine("|-damage|p2a: Gengar|0 fnt"))
        assertEquals(BattleAnnouncerCue.BURN, BattleAnnouncerCueResolver.cueForProtocolLine("|-status|p2a: Gengar|brn"))
        assertEquals(BattleAnnouncerCue.SANDSTORM, BattleAnnouncerCueResolver.cueForProtocolLine("|-weather|Sandstorm|[from] ability: Sand Stream"))
        assertEquals(BattleAnnouncerCue.HAIL, BattleAnnouncerCueResolver.cueForProtocolLine("|-weather|Snow|[from] ability: Snow Warning"))
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
        assertNull(BattleAnnouncerCueResolver.cueForNativeValue("generic_damage"))
    }

    @Test
    fun nonAnnouncerProtocolEventsRemainSilent() {
        assertNull(BattleAnnouncerCueResolver.cueForProtocolLine("|-boost|p1a: Pikachu|atk|1"))
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
        BattleAnnouncerCue.values().forEach { cue ->
            assertTrue(File("src/main/assets/${BattleAnnouncerAssets.assetPath(cue)}").isFile)
        }
    }
}
