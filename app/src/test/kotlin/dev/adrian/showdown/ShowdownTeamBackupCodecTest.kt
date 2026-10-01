package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTeamBackupCodecTest {
    @Test
    fun packsAndParsesTheOfficialTeamBackupLines() {
        val source = listOf(
            ShowdownTeam("one", "Singles", "gen9ou", ShowdownTeamCodec.pack(listOf(ShowdownTeamSet(species = "Gholdengo", moves = listOf("Make It Rain"))))),
            ShowdownTeam("two", "Rain", "gen8ou", ShowdownTeamCodec.pack(listOf(ShowdownTeamSet(species = "Pelipper", moves = listOf("Hurricane"))))),
        )

        val parsed = ShowdownTeamBackupCodec.parse(ShowdownTeamBackupCodec.pack(source))

        assertEquals(listOf("Singles", "Rain"), parsed.map { it.name })
        assertEquals(listOf("gen9ou", "gen8ou"), parsed.map { it.format })
        assertEquals(listOf("Gholdengo", "Pelipper"), parsed.map { ShowdownTeamCodec.unpack(it.packed).single().species })
    }

    @Test
    fun packsAndParsesReadableTeamBackups() {
        val source = listOf(
            ShowdownTeam("one", "Balance", "gen9ou", ShowdownTeamCodec.pack(listOf(ShowdownTeamSet(species = "Gholdengo", moves = listOf("Make It Rain")))), folder = "Tournament")
        )

        val parsed = ShowdownTeamBackupCodec.parse(ShowdownTeamBackupCodec.toText(source))

        assertEquals("Balance", parsed.single().name)
        assertEquals("gen9ou", parsed.single().format)
        assertEquals("Tournament", parsed.single().folder)
        assertTrue(ShowdownTeamCodec.toText(ShowdownTeamCodec.unpack(parsed.single().packed)).contains("makeitrain"))
    }

    @Test
    fun packsAndParsesFolderPrefixesInPackedBackups() {
        val packed = ShowdownTeamCodec.pack(listOf(ShowdownTeamSet(species = "Gholdengo", moves = listOf("Make It Rain"))))

        val parsed = ShowdownTeamBackupCodec.parse("gen9ou]Tournament/Balance|$packed")

        assertEquals("Balance", parsed.single().name)
        assertEquals("Tournament", parsed.single().folder)
    }

    @Test
    fun preservesShowdownCompatibleExtendedSetsInPackedBackups() {
        val source = ShowdownTeamSet(
            species = "Pikachu",
            moves = listOf("Thunderbolt", "Surf", "Protect", "Encore", "Volt Tackle"),
            level = 9999
        )
        val packed = ShowdownTeamCodec.pack(listOf(source))

        val parsed = ShowdownTeamBackupCodec.parse("gen9]Extended|$packed")
        val restored = ShowdownTeamCodec.unpack(parsed.single().packed).single()

        assertEquals(source.moves.map { it.lowercase().filter(Char::isLetterOrDigit) }, restored.moves)
        assertEquals(9999, restored.level)
    }

    @Test
    fun parsesAStandaloneShowdownExportAsOneImportedTeam() {
        val parsed = ShowdownTeamBackupCodec.parse(
            """Gholdengo @ Leftovers
Ability: Good as Gold
- Make It Rain"""
        )

        assertEquals(1, parsed.size)
        assertEquals("Imported team", parsed.single().name)
        assertEquals("Gholdengo", ShowdownTeamCodec.unpack(parsed.single().packed).single().species)
    }

    @Test
    fun rejectsMalformedPackedBackupLines() {
        assertTrue(ShowdownTeamBackupCodec.parse("gen9]Broken|not-a-packed-team").isEmpty())
    }

    @Test
    fun rejectsTruncatedPackedBackupRecords() {
        assertTrue(ShowdownTeamBackupCodec.parse("gen9]Broken|Pikachu|||thunderbolt").isEmpty())
    }

    @Test
    fun rejectsPackedBackupsWithInvalidNumericValues() {
        val packed = listOf(
            "Pikachu",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "",
            "10000",
            ""
        ).joinToString("|")

        assertTrue(ShowdownTeamBackupCodec.parse("gen9]Broken|$packed").isEmpty())
    }

    @Test
    fun rejectsReadableBackupsWithInvalidNumericValues() {
        assertTrue(
            ShowdownTeamBackupCodec.parse(
                """=== [gen9] Broken ===

Pikachu
Level: invalid"""
            ).isEmpty()
        )
    }

    @Test
    fun rejectsReadableBackupsWhenAnyTeamIsInvalid() {
        assertTrue(
            ShowdownTeamBackupCodec.parse(
                """=== [gen9] Valid ===

Pikachu
- Thunderbolt

=== [gen9] Broken ===

Pikachu
Level: invalid"""
            ).isEmpty()
        )
    }
}
