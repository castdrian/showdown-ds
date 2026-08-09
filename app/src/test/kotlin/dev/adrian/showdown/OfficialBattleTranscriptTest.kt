package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficialBattleTranscriptTest {
    @Test
    fun appliesTheCompleteOfficialSimulatorTranscript() {
        val session = BattleSession()
        session.setLocalUsername("ADRIAN")
        session.applyProtocolPacket(
            listOf(
                "|t:|1786098934",
                "|gametype|singles",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|gen|7",
                "|tier|[Gen 7] Custom Game",
                "|teampreview",
                "|teamsize|p1|1",
                "|teamsize|p2|1",
                "|rule|Species Clause: Limit one of each Pokémon",
                "|start",
                "|switch|p1a: Mewtwo|Mewtwo|353/353",
                "|switch|p2a: Magikarp|Magikarp, L1, F|11/11",
                "|-ability|p1a: Mewtwo|Pressure",
                "|turn|1",
                "|move|p1a: Mewtwo|Psystrike|p2a: Magikarp",
                "|-damage|p2a: Magikarp|0 fnt",
                "|faint|p2a: Magikarp",
                "|win|ADRIAN"
            )
        )

        assertEquals("Mewtwo", session.playerPokemon)
        assertEquals("Magikarp", session.opponentPokemon)
        assertEquals("0 fnt", session.opponentHp)
        assertEquals("ADRIAN won the battle.", session.status)
        assertEquals("Pressure", session.playerDetails().ability)
        assertEquals("singles", session.gameType)
        assertTrue(session.battleLog().contains("Battle type: Singles."))
        assertTrue(session.battleLog().contains("Format: [Gen 7] Custom Game"))
        assertTrue(session.battleLog().contains("Rule: Species Clause: Limit one of each Pokémon"))
        assertFalse(session.decisionAvailable)
        assertTrue(session.battleLog().any { it.contains("Psystrike") })
    }

    @Test
    fun presentsCommonSimulatorFailureAndFieldEvents() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-weather|RainDance",
                "|-fieldstart|move: Electric Terrain",
                "|-sidestart|p1: Stealth Rock",
                "|-boost|p1a: Mewtwo|spa|2",
                "|cant|p1a: Mewtwo|slp",
                "|-miss|p1a: Mewtwo|p2a: Magikarp"
            )
        )

        assertEquals("RainDance", session.battleInfo().weather)
        assertEquals("Electric Terrain", session.battleInfo().terrain)
        assertTrue(session.battleLog().any { it.contains("couldn't move") })
        assertTrue(session.battleLog().any { it.contains("missed") })
    }

    @Test
    fun appliesTheOfficialMinorBattleActionVariants() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Mewtwo|Mewtwo, L50|83/100 brn",
                "|switch|p2a: Magikarp|Magikarp, L1|11/11",
                "|-sethp|p1a: Mewtwo|70/100 brn",
                "|-endability|p1a: Mewtwo",
                "|-transform|p1a: Mewtwo|Ditto",
                "|-hitcount|p1a: Ditto|3",
                "|-waiting|p1a: Ditto|p2a: Magikarp",
                "|-zpower|p1a: Ditto",
                "|-cureteam|p1a: Ditto"
            )
        )

        assertEquals("Ditto", session.playerPokemon)
        assertEquals("70/100", session.playerHp)
        assertEquals("READY", session.playerCondition)
        assertEquals("Suppressed", session.playerDetails().ability)
        assertTrue(session.battleLog().any { it.contains("hit 3 times") })
        assertTrue(session.battleLog().any { it.contains("Z-Power") })
    }

    @Test
    fun appliesTemporaryTypesDynamaxAndOneLineBattleResults() {
        val session = BattleSession()
        session.setPokemonTypeResolver(
            mapOf(
                "Mewtwo" to listOf("PSYCHIC"),
                "Dragapult" to listOf("DRAGON", "GHOST")
            )::get
        )
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|switch|p2a: Dragapult|Dragapult, L50|100/100"
            )
        )
        session.applyProtocolLine("|-start|p1a: Mewtwo|typechange|FIRE/GHOST")
        assertEquals(listOf("FIRE", "GHOST"), session.playerActiveCombatants().single().types)
        session.applyProtocolLine("|-start|p1a: Mewtwo|typeadd|DARK")
        assertEquals(listOf("FIRE", "GHOST", "DARK"), session.playerActiveCombatants().single().types)
        session.applyProtocolLine("|-end|p1a: Mewtwo|typeadd")
        assertEquals(listOf("FIRE", "GHOST"), session.playerActiveCombatants().single().types)
        session.applyProtocolLine("|-end|p1a: Mewtwo|typechange")
        session.applyProtocolLine("|-start|p2a: Dragapult|Dynamax|Gmax|[silent]")
        assertTrue(session.opponentActiveCombatants().single().dynamaxed)
        assertTrue(session.opponentActiveCombatants().single().gMaxed)
        assertFalse(session.battleLog().any { it.contains("Dynamaxed") })
        session.applyProtocolLine("|-end|p2a: Dragapult|dynamax")
        session.applyProtocolLine("|-ohko|p2a: Dragapult")
        session.applyProtocolLine("|-combine|p1a: Mewtwo")

        assertEquals(listOf("PSYCHIC"), session.playerDetails().types)
        assertEquals(listOf("PSYCHIC"), session.playerActiveCombatants().single().types)
        assertFalse(session.opponentActiveCombatants().single().dynamaxed)
        assertFalse(session.opponentActiveCombatants().single().gMaxed)
        assertTrue(session.battleLog().contains("It's a one-hit KO!"))
        assertTrue(session.battleLog().contains("The move effects combined."))
    }

    @Test
    fun keepsSilentProtocolStateChangesOutOfTheBattleLog() {
        val session = BattleSession()
        session.setPokemonTypeResolver(mapOf("Mewtwo" to listOf("PSYCHIC"))::get)
        session.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|70/100")
        val initialLogSize = session.battleLog().size

        session.applyProtocolLine("|-heal|p1a: Mewtwo|100/100|[silent]")
        session.applyProtocolLine("|-start|p1a: Mewtwo|typechange|FIRE|[silent]")
        session.applyProtocolLine("|-start|p1a: Mewtwo|typeadd|DARK|[silent]")
        session.applyProtocolLine("|-start|p1a: Mewtwo|Focus Energy|[silent]")
        session.applyProtocolLine("|-end|p1a: Mewtwo|typeadd|[silent]")
        session.applyProtocolLine("|-end|p1a: Mewtwo|typechange|[silent]")

        assertEquals("100/100", session.playerHp)
        assertEquals(listOf("PSYCHIC"), session.playerActiveCombatants().single().types)
        assertEquals(initialLogSize, session.battleLog().size)
    }

    @Test
    fun preservesUnknownTypeProtocolStates() {
        val session = BattleSession()
        session.setPokemonTypeResolver(mapOf("Mewtwo" to listOf("PSYCHIC"))::get)
        session.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        session.applyProtocolLine("|-start|p1a: Mewtwo|typechange|???")

        assertEquals(listOf("???"), session.playerActiveCombatants().single().types)
    }

    @Test
    fun resolvesTransformTargetsFromOfficialActorPackets() {
        val session = BattleSession()
        session.setPokemonTypeResolver(
            mapOf(
                "Mewtwo" to listOf("PSYCHIC"),
                "Dragapult" to listOf("DRAGON", "GHOST")
            )::get
        )
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|switch|p2a: Dragapult|Dragapult, L50|100/100",
                "|-transform|p1a: Mewtwo|p2a: Dragapult"
            )
        )

        assertEquals("Dragapult", session.playerPokemon)
        assertEquals(listOf("DRAGON", "GHOST"), session.playerDetails().types)
    }

    @Test
    fun preservesTransformAndPerSlotBoostStateFromOfficialPackets() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|switch|p1b: Mimikyu|Mimikyu, L50|100/100",
                "|switch|p2a: Dragapult|Dragapult, L50|100/100",
                "|-boost|p1a: Mewtwo|atk|2",
                "|-boost|p1b: Mimikyu|def|1",
                "|-boost|p2a: Dragapult|spa|2"
            )
        )

        session.applyProtocolLine("|-swapboost|p1a: Mewtwo|p1b: Mimikyu|def")
        assertEquals(mapOf("atk" to 2, "def" to 1), session.battleInfo().playerBoosts)

        session.applyProtocolLine("|-transform|p1a: Mewtwo|p2a: Dragapult")
        assertEquals(mapOf("spa" to 2), session.battleInfo().playerBoosts)

        session.applyProtocolLine("|-copyboost|p1a: Dragapult|p1b: Mimikyu")
        session.applyProtocolLine("|-invertboost|p1b: Mimikyu")
        session.applyProtocolLine("|-clearnegativeboost|p1b: Mimikyu")

        assertEquals(mapOf("spa" to 2), session.battleInfo().playerBoosts)

        session.applyProtocolLine("|faint|p1a: Dragapult")
        assertTrue(session.battleInfo().playerBoosts.isEmpty())
    }

    @Test
    fun keepsMarkupBattleAnnouncementsReadableInActivity() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|raw|<div class=\"broadcast-red\">A <b>battle</b> announcement &amp; rule</div>",
                "|html|<p>The winner is <strong>ADRIAN</strong>.</p>",
                "|uhtml|notice|<span>Use /help for commands.</span>",
                "|message|ADRIAN's rating: 1053 &rarr; 1080"
            )
        )

        assertTrue(session.battleLog().contains("A battle announcement & rule"))
        assertTrue(session.battleLog().contains("The winner is ADRIAN."))
        assertTrue(session.battleLog().contains("Use /help for commands."))
        assertTrue(session.battleLog().contains("ADRIAN's rating: 1053 → 1080"))
    }

    @Test
    fun replacesUpdatedMarkupAnnouncementsInsteadOfLeavingStaleText() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|uhtml|notice|<b>Queue open</b>",
                "|uhtmlchange|notice|<b>Queue closed</b>"
            )
        )

        assertFalse(session.activityMessages().contains("Queue open"))
        assertEquals(1, session.activityMessages().count { it == "Queue closed" })
    }
}
