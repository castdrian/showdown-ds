package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTeamCodecTest {
    @Test
    fun unpacksOfficialPackedFieldsAndDefaults() {
        val team = ShowdownTeamCodec.unpack(
            "Articuno||leftovers|pressure|icebeam,hurricane,substitute,roost|Modest|252,,,252,4,||,,,30,30,|||"
        ).single()

        assertEquals("Articuno", team.nickname)
        assertEquals("Articuno", team.species)
        assertEquals("leftovers", team.item)
        assertEquals("pressure", team.ability)
        assertEquals(listOf("icebeam", "hurricane", "substitute", "roost"), team.moves)
        assertEquals(listOf(252, 0, 0, 252, 4, 0), team.evs)
        assertEquals(listOf(31, 31, 31, 30, 30, 31), team.ivs)
        assertEquals(100, team.level)
        assertEquals(255, team.happiness)
        assertEquals(10, team.dynamaxLevel)
        assertFalse(team.shiny)
    }

    @Test
    fun packsAndUnpacksAnEditableSet() {
        val set = ShowdownTeamSet(
            nickname = "Lead",
            species = "Gholdengo",
            item = "Leftovers",
            ability = "Good as Gold",
            moves = listOf("Make It Rain", "Shadow Ball", "Recover", "Nasty Plot"),
            nature = "Timid",
            evs = listOf(4, 0, 0, 252, 0, 252),
            ivs = listOf(31, 31, 31, 0, 31, 31),
            shiny = true,
            level = 50,
            happiness = 200,
            pokeBall = "Premier Ball",
            hiddenPowerType = "Ice",
            gigantamax = true,
            dynamaxLevel = 8,
            teraType = "Steel"
        )

        val packed = ShowdownTeamCodec.pack(listOf(set))
        assertEquals(
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain,shadowball,recover,nastyplot|Timid|4,,,252,,252||,,,0,,|S|50|200,premierball,Ice,G,8,Steel",
            packed
        )
        assertEquals(
            set.copy(
                item = "leftovers",
                ability = "goodasgold",
                moves = listOf("makeitrain", "shadowball", "recover", "nastyplot"),
                pokeBall = "premierball"
            ),
            ShowdownTeamCodec.unpack(packed).single()
        )
    }

    @Test
    fun omitsDefaultFieldsAndKeepsPartialValuesAligned() {
        val packed = ShowdownTeamCodec.pack(
            listOf(
                ShowdownTeamSet(
                    species = "Pikachu",
                    moves = listOf("Thunderbolt"),
                    evs = listOf(0, 252, 0, 0, 0, 4)
                )
            )
        )

        assertEquals("|Pikachu|||thunderbolt||,252,,,,4|||||", packed)
        assertEquals(listOf(0, 252, 0, 0, 0, 4), ShowdownTeamCodec.unpack(packed).single().evs)
        assertTrue(ShowdownTeamCodec.unpack("").isEmpty())
    }

    @Test
    fun validatesTeamSizeMovesAndCompetitiveLimits() {
        val errors = ShowdownTeamCodec.validate(
            listOf(
                ShowdownTeamSet(
                    species = "Pikachu",
                    moves = listOf("Thunderbolt", "Surf", "Volt Tackle", "Nasty Plot", "Protect"),
                    evs = listOf(252, 252, 0, 0, 0, 252)
                )
            )
        )

        assertTrue(errors.any { it.contains("at most four moves") })
        assertTrue(errors.any { it.contains("510 total EVs") })
    }

    @Test
    fun parsesAndExportsShowdownText() {
        val set = ShowdownTeamCodec.parse(
            """Lead (Gholdengo) (F) @ Leftovers
Ability: Good as Gold
Level: 50
Shiny: Yes
EVs: 4 HP / 252 SpA / 252 Spe
Timid Nature
IVs: 0 Atk
Tera Type: Steel
- Make It Rain
- Shadow Ball
- Recover
- Nasty Plot"""
        ).single()

        assertEquals("Lead", set.nickname)
        assertEquals("Gholdengo", set.species)
        assertEquals("F", set.gender)
        assertEquals("Leftovers", set.item)
        assertEquals("Good as Gold", set.ability)
        assertEquals(listOf(4, 0, 0, 252, 0, 252), set.evs)
        assertEquals(listOf(31, 0, 31, 31, 31, 31), set.ivs)
        assertTrue(set.shiny)
        val text = ShowdownTeamCodec.toText(listOf(set))
        assertEquals("Lead (Gholdengo) (F) @ Leftovers", text.lineSequence().first())
        assertTrue(text.contains("Tera Type: Steel"))
        assertEquals(1, ShowdownTeamCodec.parse(text).size)
    }

    @Test
    fun parsesAndExportsShowdownJson() {
        val input = """[{"name":"Lead","species":"Gholdengo","item":"Leftovers","ability":"Good as Gold","moves":["Make It Rain","Shadow Ball"],"nature":"Timid","evs":{"hp":4,"spa":252,"spe":252},"ivs":{"atk":0},"level":50,"teraType":"Steel"}]"""

        val set = ShowdownTeamCodec.parse(input).single()

        assertEquals("Lead", set.nickname)
        assertEquals("Gholdengo", set.species)
        assertEquals(listOf("Make It Rain", "Shadow Ball"), set.moves)
        assertEquals(listOf(4, 0, 0, 252, 0, 252), set.evs)
        assertEquals(listOf(31, 0, 31, 31, 31, 31), set.ivs)
        assertEquals(50, set.level)
        assertEquals("Steel", set.teraType)
        assertEquals(1, ShowdownTeamCodec.parse(ShowdownTeamCodec.toJson(listOf(set))).size)
    }
}
