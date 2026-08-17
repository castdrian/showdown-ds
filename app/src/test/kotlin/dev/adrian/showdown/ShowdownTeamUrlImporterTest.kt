package dev.adrian.showdown

import android.content.Intent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTeamUrlImporterTest {
    @Test
    fun normalizesPokePasteUrlsToTheOfficialJsonEndpoint() {
        assertEquals(
            "https://pokepast.es/0f9d6738de156f45/json",
            ShowdownTeamUrlImporter.normalize("https://pokepast.es/0f9d6738de156f45")
        )
    }

    @Test
    fun normalizesGistUrlsToTheRawEndpoint() {
        assertEquals(
            "https://gist.githubusercontent.com/user/123456/raw",
            ShowdownTeamUrlImporter.normalize("https://gist.github.com/user/123456")
        )
    }

    @Test
    fun extractsPokePasteTextAndMetadata() {
        val payload = ShowdownTeamUrlImporter.payload(
            """{"title":"Monotype sample","notes":"Format: gen9monotype","paste":"Gholdengo @ Leftovers\n- Make It Rain"}"""
        )

        assertEquals("Monotype sample", payload.name)
        assertEquals("gen9monotype", payload.format)
        assertTrue(payload.text.contains("Gholdengo @ Leftovers"))
    }

    @Test
    fun rejectsUntrustedUrls() {
        assertNull(ShowdownTeamUrlImporter.normalize("https://example.com/team"))
    }

    @Test
    fun preservesFallbackMetadataForPlainPasteText() {
        val payload = ShowdownTeamUrlImporter.payload(
            "Gholdengo @ Leftovers\n- Make It Rain",
            "Monotype sample",
            "gen9monotype"
        )

        assertEquals("Monotype sample", payload.name)
        assertEquals("gen9monotype", payload.format)
    }

    @Test
    fun acceptsSharedTeamTextAsAnIncomingIntent() {
        assertEquals(
            "Gholdengo @ Leftovers\n- Make It Rain",
            ShowdownTeamUrlImporter.intentSource(
                Intent.ACTION_SEND,
                null,
                "Gholdengo @ Leftovers\n- Make It Rain"
            )
        )
    }

    @Test
    fun acceptsPokePasteViewLinksAsAnIncomingIntent() {
        assertEquals(
            "https://pokepast.es/0f9d6738de156f45",
            ShowdownTeamUrlImporter.intentSource(
                Intent.ACTION_VIEW,
                "https://pokepast.es/0f9d6738de156f45",
                null
            )
        )
    }

    @Test
    fun doesNotConsumeUnrelatedSharedText() {
        assertNull(
            ShowdownTeamUrlImporter.intentSource(
                Intent.ACTION_SEND,
                null,
                "This is just a message"
            )
        )
    }
}
