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
    fun appliesOfficialOpenTeamSheetPacketsToPartyInspection() {
        val session = BattleSession()
        session.setLocalUsername("ADRIAN")
        session.setTeamDetailNameResolvers(
            { value -> mapOf("hydropump" to "Hydro Pump", "voltswitch" to "Volt Switch", "willowisp" to "Will-O-Wisp", "protect" to "Protect", "earthquake" to "Earthquake", "dragonclaw" to "Dragon Claw", "rockslide" to "Rock Slide")[value] ?: value },
            { value -> mapOf("leftovers" to "Leftovers", "choicescarf" to "Choice Scarf")[value] ?: value },
            { value -> mapOf("levitate" to "Levitate", "roughskin" to "Rough Skin")[value] ?: value }
        )
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|showteam|p2|Washy|Rotom-Wash|leftovers|levitate|hydropump,voltswitch,willowisp,protect|||F||||]Garchomp||choicescarf|roughskin|earthquake,dragonclaw,rockslide,protect|||M||||"
            )
        )
        session.setPokemonTypeResolver(
            mapOf(
                "Rotom-Wash" to listOf("ELECTRIC", "WATER"),
                "Garchomp" to listOf("GROUND", "DRAGON")
            )::get
        )

        assertEquals(2, session.opponentPartyDetails().size)
        assertEquals("Washy", session.opponentPartyDetails()[0].name)
        assertEquals("Leftovers", session.opponentPartyDetails()[0].item)
        assertEquals("Levitate", session.opponentPartyDetails()[0].ability)
        assertEquals(listOf("ELECTRIC", "WATER"), session.opponentPartyDetails()[0].types)
        assertEquals(listOf("Hydro Pump", "Volt Switch", "Will-O-Wisp", "Protect"), session.opponentPartyDetails()[0].moves)
        assertEquals("♂", session.opponentPartyDetails()[1].gender)
        assertTrue(session.battleLog().any { it.contains("OPPONENT revealed their team") })
    }

    @Test
    fun selectsTheViewerSideOfOfficialSplitReplayPackets() {
        val playerOne = BattleSession().apply { setLocalUsername("ADRIAN") }
        playerOne.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|split|p1",
                "|-damage|p1a: Mewtwo|90/100",
                "|-damage|p1a: Mewtwo|80/100"
            )
        )

        val playerTwo = BattleSession().apply { setLocalUsername("OPPONENT") }
        playerTwo.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|split|p1",
                "|-damage|p1a: Mewtwo|90/100",
                "|-damage|p1a: Mewtwo|80/100"
            )
        )

        assertEquals("90/100", playerOne.playerHp)
        assertEquals("80/100", playerTwo.opponentHp)
        assertFalse(playerOne.protocolHistory().any { it.startsWith("|split|") })
    }

    @Test
    fun tracksOfficialBattlePhasesAndClearsTheMessageFeedMarker() {
        val session = BattleSession()

        session.applyProtocolLine("|init|battle")
        assertEquals(BattleSession.BattlePhase.BATTLE, session.battlePhase)
        assertTrue(session.battleFeedVisible)

        session.applyProtocolLine("|teampreview")
        assertEquals(BattleSession.BattlePhase.TEAM_PREVIEW, session.battlePhase)

        session.applyProtocolLine("|start")
        assertEquals(BattleSession.BattlePhase.BATTLE, session.battlePhase)

        session.applyProtocolLine("|upkeep")
        assertEquals(BattleSession.BattlePhase.UPKEEP, session.battlePhase)

        session.applyProtocolLine("|")
        assertFalse(session.battleFeedVisible)

        session.applyProtocolLine("|-weather|RainDance")
        assertTrue(session.battleFeedVisible)

        session.applyProtocolLine("|")
        session.applyProtocolLine("|-weather|RainDance")
        assertFalse(session.battleFeedVisible)

        session.applyProtocolLine("|-weather|Sun")
        assertTrue(session.battleFeedVisible)
    }

    @Test
    fun keepsTheLatestMeaningfulBattleFeedEventAfterAConversationTurnMarker() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|turn|1",
                "|request|null"
            )
        )

        assertEquals("Go! Pikachu!", session.battleFeedText())
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
                "|-miss|p1a: Mewtwo|p2a: Magikarp",
                "|bigerror|The battle is nearing its turn limit."
            )
        )

        assertEquals("RainDance", session.battleInfo().weather)
        assertEquals("Electric Terrain", session.battleInfo().terrain)
        assertTrue(session.battleLog().any { it.contains("couldn't move") })
        assertTrue(session.battleLog().any { it.contains("missed") })
        assertTrue(session.battleLog().any { it.contains("Warning: The battle is nearing its turn limit.") })
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
    fun revealsBarrierItemsFromOfficialBlockPackets() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Gholdengo|Gholdengo, L50|100/100",
                "|switch|p2a: Gliscor|Gliscor, L50|100/100",
                "|-block|p1a: Gholdengo|item: Safety Goggles",
                "|-block|p2a: Gliscor|item: Protective Pads",
                "|-block|p1a: Gholdengo|item: Ability Shield",
                "|-block|p2a: Gliscor|Protect"
            )
        )

        assertEquals("Ability Shield", session.playerDetails().item)
        assertEquals("Protective Pads", session.opponentDetails().item)
        assertEquals(listOf("Protect"), session.opponentActiveCombatants().single().turnEffects)
        assertTrue(session.battleLog().any { it.contains("Gliscor was blocked by Protect.") })
    }

    @Test
    fun revealsAbilitiesFromOfficialActivatePackets() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Iron Valiant|Iron Valiant|100/100",
                "|-activate|p1a: Iron Valiant|ability: Quark Drive|[fromitem]",
                "|switch|p2a: Kingambit|Kingambit, L50, M|100/100",
                "|-activate|p2a: Kingambit|ability: Supreme Overlord"
            )
        )

        assertEquals("Quark Drive", session.playerDetails().ability)
        assertEquals("Supreme Overlord", session.opponentDetails().ability)
        assertTrue(session.battleLog().any { it.contains("Iron Valiant activated Quark Drive.") })
    }

    @Test
    fun hidesInternalAbilityStateTokensFromTheBattleFeed() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Iron Valiant|Iron Valiant|100/100",
                "|-activate|p1a: Iron Valiant|ability: Quark Drive|[fromitem]",
                "|-start|p1a: Iron Valiant|quarkdrivespa",
                "|-end|p1a: Iron Valiant|quarkdrivespa"
            )
        )

        assertFalse(session.battleLog().any { it.contains("quarkdrivespa", true) })
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
    fun tracksOfficialVolatileEffectsUntilTheirEndPackets() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|-start|p1a: Mewtwo|confusion",
                "|-start|p1a: Mewtwo|move: Substitute",
                "|-start|p1a: Mewtwo|move: Focus Energy",
                "|-end|p1a: Mewtwo|confusion"
            )
        )

        assertEquals(listOf("Substitute", "Focus Energy"), session.playerActiveCombatants().single().volatileEffects)
    }

    @Test
    fun keepsOfficialVolatileEffectsAcrossBattleRequests() {
        val session = BattleSession()
        session.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        session.applyProtocolLine("|-start|p1a: Mewtwo|move: Substitute")
        session.applyProtocolLine("|-singleturn|p1a: Mewtwo|Protect")
        session.applyProtocolLine("|-singlemove|p1a: Mewtwo|Destiny Bond")
        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Psychic\",\"pp\":10}]}],\"side\":{\"pokemon\":[{\"ident\":\"p1: Mewtwo\",\"details\":\"Mewtwo, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )

        assertEquals(listOf("Substitute"), session.playerActiveCombatants().single().volatileEffects)
        assertEquals(listOf("Protect"), session.playerActiveCombatants().single().turnEffects)
        assertEquals(listOf("Destiny Bond"), session.playerActiveCombatants().single().moveEffects)
    }

    @Test
    fun tracksOfficialTurnAndMoveEffectsUntilTheirProtocolBoundaries() {
        val session = BattleSession()
        session.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        session.applyProtocolLine("|-singleturn|p1a: Mewtwo|Protect")
        session.applyProtocolLine("|-singlemove|p1a: Mewtwo|Destiny Bond")

        assertEquals(listOf("Protect"), session.playerActiveCombatants().single().turnEffects)
        assertEquals(listOf("Destiny Bond"), session.playerActiveCombatants().single().moveEffects)

        session.applyProtocolLine("|turn|2")

        assertTrue(session.playerActiveCombatants().single().turnEffects.isEmpty())
        assertEquals(listOf("Destiny Bond"), session.playerActiveCombatants().single().moveEffects)

        session.applyProtocolLine("|move|p1a: Mewtwo|Psychic|p2a: Dragapult")

        assertTrue(session.playerActiveCombatants().single().moveEffects.isEmpty())
    }

    @Test
    fun clearsMoveEffectsWhenShowdownReportsAFailedMove() {
        val session = BattleSession()
        session.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        session.applyProtocolLine("|-singlemove|p1a: Mewtwo|Destiny Bond")
        session.applyProtocolLine("|cant|p1a: Mewtwo|par")

        assertTrue(session.playerActiveCombatants().single().moveEffects.isEmpty())
    }

    @Test
    fun validatesRoostAgainstCurrentTerastallizedTypes() {
        val flyingSession = BattleSession()
        flyingSession.setPokemonTypeResolver(mapOf("Mewtwo" to listOf("FLYING", "PSYCHIC"))::get)
        flyingSession.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        flyingSession.applyProtocolLine("|-terastallize|p1a: Mewtwo|FIRE")
        flyingSession.applyProtocolLine("|-singleturn|p1a: Mewtwo|Roost")

        assertTrue(flyingSession.playerActiveCombatants().single().turnEffects.isEmpty())

        val normalFlyingSession = BattleSession()
        normalFlyingSession.setPokemonTypeResolver(mapOf("Mewtwo" to listOf("FLYING", "PSYCHIC"))::get)
        normalFlyingSession.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        normalFlyingSession.applyProtocolLine("|-singleturn|p1a: Mewtwo|Roost")

        assertEquals(listOf("PSYCHIC"), normalFlyingSession.playerActiveCombatants().single().types)
        normalFlyingSession.applyProtocolLine("|turn|2")
        assertEquals(listOf("FLYING", "PSYCHIC"), normalFlyingSession.playerActiveCombatants().single().types)

        val teraFlyingSession = BattleSession()
        teraFlyingSession.setPokemonTypeResolver(mapOf("Mewtwo" to listOf("PSYCHIC"))::get)
        teraFlyingSession.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        teraFlyingSession.applyProtocolLine("|-terastallize|p1a: Mewtwo|FLYING")
        teraFlyingSession.applyProtocolLine("|-singleturn|p1a: Mewtwo|Roost")

        assertEquals(listOf("Roost"), teraFlyingSession.playerActiveCombatants().single().turnEffects)
        assertEquals(listOf("NORMAL"), teraFlyingSession.playerActiveCombatants().single().types)
        teraFlyingSession.applyProtocolLine("|turn|2")
        assertEquals(listOf("FLYING"), teraFlyingSession.playerActiveCombatants().single().types)

        val stellarSession = BattleSession()
        stellarSession.setPokemonTypeResolver(mapOf("Mewtwo" to listOf("FLYING", "PSYCHIC"))::get)
        stellarSession.applyProtocolLine("|switch|p1a: Mewtwo|Mewtwo, L50|100/100")
        stellarSession.applyProtocolLine("|-terastallize|p1a: Mewtwo|STELLAR")
        stellarSession.applyProtocolLine("|-singleturn|p1a: Mewtwo|Roost")

        assertEquals(listOf("PSYCHIC"), stellarSession.playerActiveCombatants().single().types)
        stellarSession.applyProtocolLine("|turn|2")
        assertEquals(listOf("FLYING", "PSYCHIC"), stellarSession.playerActiveCombatants().single().types)
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
        session.applyProtocolLine("|-block|p1a: Mewtwo|Protect|[silent]")

        assertEquals("100/100", session.playerHp)
        assertEquals(listOf("PSYCHIC"), session.playerActiveCombatants().single().types)
        assertEquals(listOf("Protect"), session.playerActiveCombatants().single().turnEffects)
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
    fun keepsInactiveFormTypeAndFaintPacketsOffThePrimaryCard() {
        val session = BattleSession()
        session.setPokemonTypeResolver(
            mapOf(
                "Incineroar" to listOf("FIRE", "DARK"),
                "Rotom-Wash" to listOf("ELECTRIC", "WATER"),
                "Rotom-Frost" to listOf("ELECTRIC", "ICE")
            )::get
        )
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Rotom-Wash|Rotom-Wash, L50|100/100",
                "|-start|p1b: Rotom-Wash|typechange|ELECTRIC",
                "|-terastallize|p1b: Rotom-Wash|ICE",
                "|detailschange|p1b: Rotom-Wash|Rotom-Frost, L50",
                "|faint|p1b: Rotom-Frost"
            )
        )

        assertEquals("Incineroar", session.playerPokemon)
        assertEquals(listOf("FIRE", "DARK"), session.playerDetails().types)
        assertEquals("Rotom-Frost", session.teamMemberDetails(4).name)
        assertEquals("FNT", session.teamMemberDetails(4).condition)
    }

    @Test
    fun carriesBoostsThroughOfficialBatonPassSwitches() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Ninjask|Ninjask, L50|100/100",
                "|-boost|p1a: Ninjask|spe|2",
                "|-activate|p1a: Ninjask|move: Baton Pass",
                "|switch|p1a: Smeargle|Smeargle, L50|100/100"
            )
        )

        assertEquals(mapOf("spe" to 2), session.battleInfo().playerBoosts)

        session.applyProtocolLine("|-boost|p1a: Smeargle|atk|1")
        session.applyProtocolLine("|switch|p1a: Vaporeon|Vaporeon, L50|100/100|[from] move: Baton Pass")

        assertEquals(mapOf("spe" to 2, "atk" to 1), session.battleInfo().playerBoosts)
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
