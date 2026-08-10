package dev.adrian.showdown

import org.junit.Assert.assertEquals
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
}
