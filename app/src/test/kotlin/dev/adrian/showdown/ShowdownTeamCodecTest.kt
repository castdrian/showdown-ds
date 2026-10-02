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
    fun allowsShowdownNormalizedPackedNumbersForServerValidation() {
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
            "101",
            "300,,pokeball,,11"
        ).joinToString("|")

        val team = ShowdownTeamCodec.unpack(packed).single()

        assertEquals(101, team.level)
        assertEquals(300, team.happiness)
        assertEquals(11, team.dynamaxLevel)
        assertTrue(ShowdownTeamCodec.validate(listOf(team)).isEmpty())
    }

    @Test
    fun rejectsPackedValuesBeyondShowdownEngineLimits() {
        val packed = listOf("Pikachu", "", "", "", "", "", "", "", "", "", "100000", "").joinToString("|")

        val errors = ShowdownTeamCodec.validate(ShowdownTeamCodec.unpack(packed))

        assertTrue(errors.any { it.contains("invalid level") })
    }

    @Test
    fun marksUnparseablePackedNumbersAsMalformed() {
        val packed = listOf("Pikachu", "", "", "", "", "", "invalid,,,,,", "", "", "", "", "not-a-number,,pokeball,,invalid")
            .joinToString("|")
        val team = ShowdownTeamCodec.unpack(packed).single()

        assertTrue(team.malformed)
        assertTrue(ShowdownTeamCodec.validate(listOf(team)).any { it.contains("malformed fields") })
    }

    @Test
    fun rejectsMalformedPackedBooleanAndAdvancedFields() {
        val packed = listOf("Pikachu", "", "", "", "", "", "", "", "", "X", "", "").joinToString("|")
        val advanced = listOf("Pikachu", "", "", "", "", "", "", "", "", "", "", "100,Ice,pokeball,,10,Electric,extra").joinToString("|")

        assertTrue(ShowdownTeamCodec.validateImport(ShowdownTeamCodec.unpack(packed)).isNotEmpty())
        assertTrue(ShowdownTeamCodec.validateImport(ShowdownTeamCodec.unpack(advanced)).isNotEmpty())
    }

    @Test
    fun preservesOfficialAbilitySlotsWhenRepacking() {
        val team = ShowdownTeamCodec.unpack(
            "Pikachu|Pikachu||h|thunderbolt||||||||"
        ).single()

        assertEquals("H", ShowdownTeamCodec.pack(listOf(team)).split('|').getOrNull(3))
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
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain,shadowball,recover,nastyplot|Timid|4,,,252,,252||,,,0,,|S|50|200,Ice,premierball,G,8,Steel",
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

        assertEquals("Pikachu||||thunderbolt||,252,,,,4|||||", packed)
        assertEquals(listOf(0, 252, 0, 0, 0, 4), ShowdownTeamCodec.unpack(packed).single().evs)
        assertTrue(ShowdownTeamCodec.unpack("").isEmpty())
    }

    @Test
    fun preservesShowdownMaximumEvValue() {
        val set = ShowdownTeamSet(
            species = "Pikachu",
            moves = listOf("Thunderbolt"),
            evs = listOf(255, 255, 0, 0, 0, 0)
        )

        val packed = ShowdownTeamCodec.pack(listOf(set))

        assertTrue(packed.contains("255,255,,,,"))
        assertEquals(set.evs, ShowdownTeamCodec.unpack(packed).single().evs)
        assertTrue(ShowdownTeamCodec.validate(listOf(set)).isEmpty())
    }

    @Test
    fun preservesMalformedPackedStatValuesForValidation() {
        val packed = listOf(
            "Pikachu",
            "",
            "",
            "",
            "",
            "",
            "invalid,,,,,",
            "",
            "40,,,,,",
            "",
            "",
            ""
        ).joinToString("|")

        val team = ShowdownTeamCodec.unpack(packed).single()

        assertEquals(-1, team.evs[0])
        assertEquals(40, team.ivs[0])
        assertTrue(team.malformed)
        assertTrue(ShowdownTeamCodec.validate(listOf(team)).any { it.contains("malformed fields") })
    }

    @Test
    fun parsesOfficialSeparatorsLegacyAbilityAndTildeMoves() {
        val sets = ShowdownTeamCodec.parse(
            """Pikachu @ Light Ball
Trait: Static
~ Thunderbolt
~ Hidden Power [Ice]
---
Snorlax @ Leftovers
Ability: Thick Fat
- Frustration"""
        )

        assertEquals(2, sets.size)
        assertEquals("Static", sets[0].ability)
        assertEquals(listOf("Thunderbolt", "Hidden Power Ice"), sets[0].moves)
        assertEquals(0, sets[1].happiness)
    }

    @Test
    fun treatsNoItemAsAnEmptyOfficialItem() {
        val set = ShowdownTeamCodec.parse(
            """Pikachu @ No Item
Ability: Static
- Thunderbolt"""
        ).single()

        assertEquals("", set.item)
        assertEquals("Pikachu\nAbility: Static\n- Thunderbolt", ShowdownTeamCodec.toText(listOf(set)))
    }

    @Test
    fun unpacksOfficialAdvancedFieldOrder() {
        val team = ShowdownTeamCodec.unpack(
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain|Timid||||S|50|200,Ice,premierball,G,8,Steel"
        ).single()

        assertEquals("premierball", team.pokeBall)
        assertEquals("Ice", team.hiddenPowerType)
        assertTrue(team.gigantamax)
        assertEquals(8, team.dynamaxLevel)
        assertEquals("Steel", team.teraType)
    }

    @Test
    fun unpacksTeamsSavedWithThePreviousAdvancedFieldOrder() {
        val team = ShowdownTeamCodec.unpack(
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain|Timid||||S|50|200,premierball,Ice,G,8,Steel"
        ).single()

        assertEquals("premierball", team.pokeBall)
        assertEquals("Ice", team.hiddenPowerType)
        assertTrue(team.gigantamax)
        assertEquals(8, team.dynamaxLevel)
        assertEquals("Steel", team.teraType)
    }

    @Test
    fun keepsOfficialPackedTeamsWithAnEmptyHiddenPowerFieldReadable() {
        val team = ShowdownTeamCodec.unpack(
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain|Timid||||S|50|200,,premierball,G,8,Steel"
        ).single()

        assertEquals("premierball", team.pokeBall)
        assertEquals("", team.hiddenPowerType)
    }

    @Test
    fun keepsOfficialPackedTeamsWithAnUnknownBallAndEmptyHiddenPowerReadable() {
        val team = ShowdownTeamCodec.unpack(
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain|Timid||||S|50|200,,customball,G,8,Steel"
        ).single()

        assertEquals("customball", team.pokeBall)
        assertEquals("", team.hiddenPowerType)
    }

    @Test
    fun keepsPreviousExportsWithAnEmptyHiddenPowerFieldReadable() {
        val team = ShowdownTeamCodec.unpack(
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain|Timid||||S|50|200,premierball,,G,8,Steel"
        ).single()

        assertEquals("premierball", team.pokeBall)
        assertEquals("", team.hiddenPowerType)
    }

    @Test
    fun keepsPreviousExportsWithAnUnknownBallAndEmptyHiddenPowerReadable() {
        val team = ShowdownTeamCodec.unpack(
            "Lead|Gholdengo|leftovers|goodasgold|makeitrain|Timid||||S|50|200,customball,,G,8,Steel"
        ).single()

        assertEquals("customball", team.pokeBall)
        assertEquals("", team.hiddenPowerType)
    }

    @Test
    fun allowsFormatSpecificTeamSizeMoveCountAndEvBudgetForServerValidation() {
        val sets = (1..7).map { index ->
            ShowdownTeamSet(
                species = "Pikachu$index",
                moves = listOf("Thunderbolt", "Surf", "Volt Tackle", "Nasty Plot", "Protect"),
                evs = listOf(252, 252, 0, 0, 0, 252),
                level = 101,
                gender = "N",
                happiness = 300,
                dynamaxLevel = 11
            )
        }

        assertTrue(ShowdownTeamCodec.validate(sets).isEmpty())
    }

    @Test
    fun allowsShowdownDefaultLevelAndFormatDependentStatValues() {
        val set = ShowdownTeamSet(
            species = "Pikachu",
            level = 0,
            evs = listOf(300, 252, 252, 0, 0, 0),
            ivs = listOf(32, 31, 31, 31, 31, 31)
        )

        assertTrue(ShowdownTeamCodec.validate(listOf(set)).isEmpty())
    }

    @Test
    fun acceptsShowdownEngineMaximumTeamMoveAndLevelValues() {
        val sets = (1..24).map { index ->
            ShowdownTeamSet(
                species = "Pokémon$index",
                moves = (1..24).map { move -> "Move$move" },
                level = 99999
            )
        }

        assertTrue(ShowdownTeamCodec.validate(sets).isEmpty())
    }

    @Test
    fun rejectsValuesBeyondShowdownEngineMaximums() {
        val oversizedTeam = (1..25).map { index -> ShowdownTeamSet(species = "Pokémon$index") }
        val oversizedMoves = ShowdownTeamSet(species = "Pikachu", moves = (1..25).map { "Move$it" })
        val oversizedLevel = ShowdownTeamSet(species = "Pikachu", level = 100000)

        assertTrue(ShowdownTeamCodec.validate(oversizedTeam).any { it.contains("at most 24") })
        assertTrue(ShowdownTeamCodec.validate(listOf(oversizedMoves)).any { it.contains("at most 24") })
        assertTrue(ShowdownTeamCodec.validate(listOf(oversizedLevel)).any { it.contains("invalid level") })
    }

    @Test
    fun allowsDuplicateMovesWhenFormatRulesPermitThem() {
        val errors = ShowdownTeamCodec.validate(
            listOf(
                ShowdownTeamSet(
                    species = "Pikachu",
                    moves = listOf("Thunderbolt", "thunderbolt")
                )
            )
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun allowsGenderlessSetsForFormatValidation() {
        val errors = ShowdownTeamCodec.validate(
            listOf(
                ShowdownTeamSet(
                    species = "Pikachu",
                    gender = "N"
                )
            )
        )

        assertTrue(errors.isEmpty())
    }

    @Test
    fun preservesGenderlessOfficialImports() {
        val set = ShowdownTeamSet(species = "Magnemite", gender = "N")

        val restored = ShowdownTeamCodec.unpack(ShowdownTeamCodec.pack(listOf(set))).single()

        assertEquals("N", restored.gender)
        assertTrue(ShowdownTeamCodec.validateImport(listOf(restored)).isEmpty())
        assertEquals("N", ShowdownTeamCodec.parse(ShowdownTeamCodec.toText(listOf(set))).single().gender)
    }

    @Test
    fun acceptsCaseInsensitiveSupportedGenderValues() {
        assertTrue(ShowdownTeamCodec.validate(listOf(ShowdownTeamSet(species = "Pikachu", gender = "m"))).isEmpty())
        assertTrue(ShowdownTeamCodec.validate(listOf(ShowdownTeamSet(species = "Pikachu", gender = "F"))).isEmpty())
    }

    @Test
    fun advancedOnlySetStillRequiresSpecies() {
        val errors = ShowdownTeamCodec.validate(listOf(ShowdownTeamSet(shiny = true)))

        assertTrue(errors.any { it.contains("needs a species") })
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
    fun preservesMalformedTextStatClausesForValidation() {
        val set = ShowdownTeamCodec.parse(
            """Pikachu
EVs: invalid
IVs: 31 Unknown"""
        ).single()

        assertEquals(-1, set.evs[0])
        assertEquals(-1, set.ivs[0])
        val errors = ShowdownTeamCodec.validate(listOf(set))
        assertTrue(set.malformed)
        assertTrue(errors.any { it.contains("malformed fields") })
    }

    @Test
    fun preservesInvalidTextNumbersForValidation() {
        val set = ShowdownTeamCodec.parse(
            """Pikachu
Level: 101
Happiness: invalid
Dynamax Level: 11"""
        ).single()

        assertEquals(101, set.level)
        assertEquals(-1, set.happiness)
        assertEquals(11, set.dynamaxLevel)
        val errors = ShowdownTeamCodec.validate(listOf(set))
        assertTrue(set.malformed)
        assertTrue(errors.any { it.contains("malformed fields") })
    }

    @Test
    fun rejectsBlankSpecifiedTextValuesInsteadOfDefaultingThem() {
        val set = ShowdownTeamCodec.parse(
            """Pikachu
Level:
Happiness:
Dynamax Level:
EVs:
IVs:"""
        ).single()

        assertEquals(-1, set.level)
        assertEquals(-1, set.happiness)
        assertEquals(-1, set.dynamaxLevel)
        assertEquals(-1, set.evs[0])
        assertEquals(-1, set.ivs[0])
        assertTrue(ShowdownTeamCodec.validateImport(listOf(set)).isNotEmpty())
    }

    @Test
    fun rejectsMalformedTextBooleanValues() {
        val set = ShowdownTeamCodec.parse(
            """Pikachu
Shiny: maybe
Gigantamax: sometimes"""
        ).single()

        assertTrue(ShowdownTeamCodec.validateImport(listOf(set)).isNotEmpty())
    }

    @Test
    fun parsesBetaClientExportFormat() {
        val set = ShowdownTeamCodec.parse(
            """Articuno
[Pressure] @ Leftovers
- Ice Beam
- Hurricane
- Substitute
- Roost
EVs: 252 HP / - Atk / 252+ SpA / 4 SpD (Modest)
IVs: 30 SpA / 30 SpD"""
        ).single()

        assertEquals("Articuno", set.species)
        assertEquals("Pressure", set.ability)
        assertEquals("Leftovers", set.item)
        assertEquals(listOf("Ice Beam", "Hurricane", "Substitute", "Roost"), set.moves)
        assertEquals(listOf(252, 0, 0, 252, 4, 0), set.evs)
        assertEquals("Modest", set.nature)
        assertEquals(listOf(31, 31, 31, 30, 30, 31), set.ivs)
    }

    @Test
    fun parsesAndExportsShowdownJson() {
        val input = """[{"name":"Lead","species":"Gholdengo","item":"Leftovers","ability":"Good as Gold","moves":["Make It Rain","Shadow Ball"],"nature":"Timid","evs":{"hp":4,"spa":252,"spe":252},"ivs":{"atk":0},"level":50,"hiddenpowertype":"Ice","teratype":"Steel"}]"""

        val set = ShowdownTeamCodec.parse(input).single()

        assertEquals("Lead", set.nickname)
        assertEquals("Gholdengo", set.species)
        assertEquals(listOf("Make It Rain", "Shadow Ball"), set.moves)
        assertEquals(listOf(4, 0, 0, 252, 0, 252), set.evs)
        assertEquals(listOf(31, 0, 31, 31, 31, 31), set.ivs)
        assertEquals(50, set.level)
        assertEquals("Ice", set.hiddenPowerType)
        assertEquals("Steel", set.teraType)
        val exported = ShowdownTeamCodec.toJson(listOf(set))
        assertTrue(exported.contains("\"hpType\":\"Ice\""))
        assertTrue(exported.contains("\"teraType\":\"Steel\""))
        assertEquals(1, ShowdownTeamCodec.parse(exported).size)
    }

    @Test
    fun parsesAndExportsOfficialAdvancedJsonFields() {
        val set = ShowdownTeamCodec.parse(
            """[{"name":"Sparky","species":"Pikachu","happiness":120,"pokeball":"Luxury Ball","hiddenpowertype":"Ice","gigantamax":true,"dynamaxlevel":4,"teratype":"Electric"}]"""
        ).single()

        assertEquals(120, set.happiness)
        assertEquals("Luxury Ball", set.pokeBall)
        assertEquals("Ice", set.hiddenPowerType)
        assertTrue(set.gigantamax)
        assertEquals(4, set.dynamaxLevel)
        assertEquals("Electric", set.teraType)

        val exported = ShowdownTeamCodec.toJson(listOf(set))
        assertTrue(exported.contains("\"pokeball\":\"Luxury Ball\""))
        assertTrue(exported.contains("\"hpType\":\"Ice\""))
        assertTrue(exported.contains("\"gigantamax\":true"))
        assertTrue(exported.contains("\"dynamaxLevel\":4"))
        assertTrue(exported.contains("\"teraType\":\"Electric\""))
        assertEquals(set, ShowdownTeamCodec.parse(exported).single())
    }

    @Test
    fun preservesInvalidJsonNumbersForValidation() {
        val set = ShowdownTeamCodec.parse(
            """[{"species":"Pikachu","level":"invalid","happiness":300,"dynamaxLevel":"invalid","evs":{"hp":"invalid"},"ivs":{"atk":40}}]"""
        ).single()

        assertEquals(-1, set.level)
        assertEquals(300, set.happiness)
        assertEquals(-1, set.dynamaxLevel)
        assertEquals(-1, set.evs[0])
        assertEquals(40, set.ivs[1])
        assertTrue(set.malformed)
        assertTrue(ShowdownTeamCodec.validate(listOf(set)).any { it.contains("malformed fields") })
    }

    @Test
    fun rejectsNonIntegralJsonNumbersInsteadOfTruncatingThem() {
        val set = ShowdownTeamCodec.parse(
            """[{"species":"Pikachu","level":50.5,"evs":{"hp":1.5},"ivs":{"atk":30.5},"dynamaxLevel":4.5}]"""
        ).single()

        assertEquals(-1, set.level)
        assertEquals(-1, set.evs[0])
        assertEquals(-1, set.ivs[1])
        assertEquals(-1, set.dynamaxLevel)
    }

    @Test
    fun rejectsNonObjectJsonEntriesInsteadOfSilentlyDroppingThem() {
        val sets = ShowdownTeamCodec.parse("""[{"species":"Pikachu"},"invalid"]""")

        assertEquals(2, sets.size)
        assertTrue(ShowdownTeamCodec.validateImport(sets).isNotEmpty())
    }

    @Test
    fun rejectsMalformedJsonFieldTypesInsteadOfDefaultingThem() {
        val sets = ShowdownTeamCodec.parse(
            """[{"species":"Pikachu","moves":"Thunderbolt","shiny":"yes","evs":[],"ivs":{"hp":31}}]"""
        )

        assertTrue(ShowdownTeamCodec.validateImport(sets).isNotEmpty())
    }

    @Test
    fun rejectsTruncatedPackedImports() {
        val sets = ShowdownTeamCodec.parseImport("Pikachu|||thunderbolt")

        assertTrue(ShowdownTeamCodec.validateImport(sets).isNotEmpty())
    }

    @Test
    fun exportsHiddenPowerWithShowdownBrackets() {
        val text = ShowdownTeamCodec.toText(
            listOf(ShowdownTeamSet(species = "Pikachu", moves = listOf("Hidden Power Ice")))
        )

        assertTrue(text.contains("- Hidden Power [Ice]"))
    }
}
