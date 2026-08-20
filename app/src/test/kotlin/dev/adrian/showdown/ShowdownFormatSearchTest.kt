package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownFormatSearchTest {
    private val formats = listOf(
        BattleSession.MatchFormat("gen9randombattle", "[Gen 9] Random Battle", "Gen 9 Random"),
        BattleSession.MatchFormat("gen9doublesou", "[Gen 9] Doubles OU", "Gen 9 Doubles OU"),
        BattleSession.MatchFormat("gen9ou", "[Gen 9] OU", "Gen 9 OU")
    )

    @Test
    fun blankQueriesKeepTheAdvertisedOrder() {
        assertEquals(formats, ShowdownFormatSearch.filter(formats, "   "))
    }

    @Test
    fun searchMatchesLabelsMenuLabelsAndIds() {
        assertEquals(listOf(formats[1]), ShowdownFormatSearch.filter(formats, "doubles ou"))
        assertEquals(listOf(formats[0]), ShowdownFormatSearch.filter(formats, "random"))
        assertEquals(listOf(formats[2]), ShowdownFormatSearch.filter(formats, "gen9ou"))
    }

    @Test
    fun unmatchedQueriesReturnNoFormats() {
        assertTrue(ShowdownFormatSearch.filter(formats, "vintage triples").isEmpty())
    }
}
