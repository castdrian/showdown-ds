package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownLadderBadgeTest {
    @Test
    fun mapsUpstreamBadgeFormatsToShowdownMedalAssets() {
        assertEquals(
            "sprites/misc/randombattle_bronze.png",
            ShowdownLadderBadge.fromProtocolValue("bronze|gen9randombattle|100-11")?.assetPath
        )
        assertEquals(
            "sprites/misc/ou_gold.png",
            ShowdownLadderBadge.fromProtocolValue("gold|gen9ou|10-11")?.assetPath
        )
        assertEquals(
            "sprites/misc/rotating_silver.png",
            ShowdownLadderBadge.fromProtocolValue("silver|gen9uu|100-11")?.assetPath
        )
        assertEquals(
            "sprites/misc/none_silver.png",
            ShowdownLadderBadge.fromProtocolValue("silver|challengecup|100-11")?.assetPath
        )
    }

    @Test
    fun rejectsIncompleteOrUnsafeBadgeValues() {
        assertNull(ShowdownLadderBadge.fromProtocolValue("bronze|gen9ou"))
        assertNull(ShowdownLadderBadge.fromProtocolValue("../bronze|gen9ou|100-11"))
        assertNull(ShowdownLadderBadge.fromProtocolValue("bronze|https://example.com|100-11"))
        assertNull(ShowdownLadderBadge.fromProtocolValue("bronze|gen9ou|top-11"))
    }

    @Test
    fun formatsBadgeAccessibilityAndSeasonMetadata() {
        val badge = checkNotNull(ShowdownLadderBadge.fromProtocolValue("bronze|gen9randombattle|100-11"))

        assertEquals("11", badge.season)
        assertEquals("User is Top 100 on the gen9randombattle Ladder", badge.accessibilityLabel)
    }

    @Test
    fun matchesShowdownSidebarVisibleBadgeLimit() {
        val badges = listOf("bronze", "silver", "gold", "platinum").map { type ->
            checkNotNull(ShowdownLadderBadge.fromProtocolValue("$type|gen9ou|100-11"))
        }

        assertEquals(listOf("bronze", "silver", "gold"), ShowdownLadderBadgePresentation.visible(badges).map { it.type })
    }

    @Test
    fun keepsBadgesWithEachTrainerWhenOpponentCardsAreReordered() {
        val badgesBySide = mapOf(
            "p2" to listOf(checkNotNull(ShowdownLadderBadge.fromProtocolValue("bronze|gen9ou|100-11"))),
            "p3" to listOf(checkNotNull(ShowdownLadderBadge.fromProtocolValue("silver|gen9ou|100-11"))),
            "p4" to listOf(checkNotNull(ShowdownLadderBadge.fromProtocolValue("gold|gen9ou|100-11")))
        )

        assertEquals(
            listOf("gold"),
            ShowdownLadderBadgePresentation.forStatusCard("p4", emptySet(), badgesBySide).map { it.type }
        )
        assertEquals(
            listOf("silver"),
            ShowdownLadderBadgePresentation.forStatusCard("p3", setOf("p4"), badgesBySide).map { it.type }
        )
        assertEquals(
            listOf("bronze"),
            ShowdownLadderBadgePresentation.forStatusCard("p2", setOf("p4", "p3"), badgesBySide).map { it.type }
        )
        assertEquals(
            emptyList<ShowdownLadderBadge>(),
            ShowdownLadderBadgePresentation.forStatusCard("p4", setOf("p4", "p3", "p2"), badgesBySide)
        )
    }

    @Test
    fun storesDistinctBadgesByShowdownSideWithoutAddingBattleLogText() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }
        val opponentBadge = "bronze|gen9randombattle|100-11"
        session.applyProtocolPacket(
            listOf(
                "|badge|p2|$opponentBadge",
                "|badge|p2|$opponentBadge",
                "|badge|p1|gold|gen9ou|10-11",
                "|player|p1|ADRIAN||",
                "|player|p2|MIRA||"
            )
        )

        assertEquals(
            listOf(checkNotNull(ShowdownLadderBadge.fromProtocolValue(opponentBadge))),
            session.battleInfo().ladderBadgesBySide["p2"]
        )
        assertEquals(
            listOf(checkNotNull(ShowdownLadderBadge.fromProtocolValue("gold|gen9ou|10-11"))),
            session.battleInfo().ladderBadgesBySide["p1"]
        )
        assertFalse(session.battleLog().any { it.contains("badge", true) })
    }

    @Test
    fun clearsBadgesWhenTheNextBattleStarts() {
        val session = BattleSession()
        session.applyProtocolPacket(listOf("|badge|p1|gold|gen9ou|10-11"))
        session.applyProtocolPacket(listOf("|init|battle"))

        assertEquals(emptyMap<String, List<ShowdownLadderBadge>>(), session.battleInfo().ladderBadgesBySide)
    }

}
