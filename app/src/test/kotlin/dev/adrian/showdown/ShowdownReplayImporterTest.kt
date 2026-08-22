package dev.adrian.showdown

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownReplayImporterTest {
    @Test
    fun extractsUploadedReplayLinksFromShowdownPopups() {
        assertEquals(
            "https://replay.pokemonshowdown.com/gen9ou-123",
            ShowdownReplayImporter.uploadUrl(
                "|popup||html|Your replay has been uploaded! <a href=\"https://replay.pokemonshowdown.com/gen9ou-123\">Open</a>"
            )
        )
        assertNull(ShowdownReplayImporter.uploadUrl("|popup|Replay upload failed"))
    }

    @Test
    fun normalizesOfficialReplayLinks() {
        assertEquals(
            "https://replay.pokemonshowdown.com/gen9ou-123.json",
            ShowdownReplayImporter.normalize("https://replay.pokemonshowdown.com/gen9ou-123")
        )
        assertEquals(
            "https://replay.pokemonshowdown.com/gen9ou-123.json",
            ShowdownReplayImporter.normalize("https://pokemonshowdown.com/replay/gen9ou-123.json")
        )
        assertNull(ShowdownReplayImporter.normalize("https://example.com/gen9ou-123"))
    }

    @Test
    fun acceptsReplayLinksEmbeddedInSharedText() {
        assertEquals(
            "https://replay.pokemonshowdown.com/gen9ou-123.json",
            ShowdownReplayImporter.normalize("Check this replay: https://replay.pokemonshowdown.com/gen9ou-123")
        )
        assertEquals(
            "https://replay.pokemonshowdown.com/gen9ou-123.json",
            ShowdownReplayImporter.intentSource(
                Intent.ACTION_SEND,
                null,
                "https://pokemonshowdown.com/replay/gen9ou-123.json"
            )?.let(ShowdownReplayImporter::normalize)
        )
        assertNull(ShowdownReplayImporter.intentSource(Intent.ACTION_SEND, null, "not a replay"))
    }

    @Test
    fun parsesReplayMetadataAndSimulatorLog() {
        val replay = ShowdownReplayImporter.payload(
            """{"id":"gen9ou-123","format":"[Gen 9] OU","players":["Alice","Bob"],"log":["|init|battle","|win|Alice"]}"""
        )

        assertEquals("gen9ou-123", replay.id)
        assertEquals("[Gen 9] OU", replay.format)
        assertEquals(listOf("Alice", "Bob"), replay.players)
        assertEquals("|init|battle\n|win|Alice", replay.log)
        assertEquals("[[Gen 9] OU] Alice vs. Bob", replay.title)
    }

    @Test
    fun resolvesReplayFormatBeforePlaybackStarts() {
        val replay = ShowdownReplayPayload(
            "gen8randombattle-123",
            "[Gen 8] Random Battle",
            emptyList(),
            "|init|battle"
        )
        val fallbackReplay = ShowdownReplayPayload(
            "smogtours-gen7randombattle-123",
            "",
            emptyList(),
            "|init|battle"
        )

        assertEquals(
            BattleSession.MatchFormat.GEN8_RANDOM,
            ShowdownReplayImporter.matchFormat(replay, BattleSession.MatchFormat.defaults)
        )
        assertEquals(
            BattleSession.MatchFormat.GEN7_RANDOM,
            ShowdownReplayImporter.matchFormat(fallbackReplay, BattleSession.MatchFormat.defaults)
        )
    }

    @Test
    fun buildsOfficialReplaySearchUrlsFromNormalizedFilters() {
        assertEquals(
            "https://replay.pokemonshowdown.com/search.json?user=Alice&user2=Bob&format=gen9ou&before=1700000000",
            ShowdownReplaySearch.url(
                ShowdownReplaySearchQuery(" Alice ", " Bob ", " GEN9OU ", 1_700_000_000L)
            )
        )
    }

    @Test
    fun parsesReplaySearchPagesAndUsesTheExtraResultForPagination() {
        val entries = (1..51).joinToString(",") { index ->
            "{\"id\":\"gen9ou-$index\",\"format\":\"gen9ou\",\"players\":[\"Alice\",\"Bob\"],\"uploadtime\":${2_000_000_000L - index}}"
        }

        val page = ShowdownReplaySearch.page("[$entries]")

        assertEquals(50, page.entries.size)
        assertEquals("gen9ou-1", page.entries.first().id)
        assertEquals("Alice vs. Bob", page.entries.first().title)
        assertTrue(page.hasMore)
        assertEquals(1_999_999_949L, page.nextBefore)
    }

    @Test
    fun acceptsObjectWrappedSearchResultsAndStopsAtTheLastPage() {
        val page = ShowdownReplaySearch.page(
            "{\"replays\":[{\"id\":\"gen9ou-1\",\"format\":\"[Gen 9] OU\",\"players\":[\"Alice\",\"Bob\"],\"uploadtime\":1700000000,\"rating\":1542}]}"
        )

        assertEquals(1, page.entries.size)
        assertEquals(1542, page.entries.single().rating)
        assertFalse(page.hasMore)
        assertNull(page.nextBefore)
    }
}
