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
    fun exposesSplitPrivateLinesOnlyToTheExactLivePlayer() {
        val packet = listOf(
            "|gametype|multi",
            "|player|p1|RED||",
            "|player|p2|BLUE||",
            "|player|p3|GREEN||",
            "|player|p4|YELLOW||",
            "|switch|p1a: Pikachu|Pikachu, L50|100/100",
            "|switch|p2a: Eevee|Eevee, L50|100/100",
            "|switch|p3a: Raichu|Raichu, L50|100/100",
            "|switch|p4a: Pidgeot|Pidgeot, L50|100/100",
            "|split|p1",
            "|-damage|p1a: Pikachu|90/100",
            "|-damage|p1a: Pikachu|80/100",
            "|split|p2",
            "|-damage|p2a: Eevee|90/100",
            "|-damage|p2a: Eevee|80/100",
            "|split|p3",
            "|-damage|p3a: Raichu|90/100",
            "|-damage|p3a: Raichu|80/100",
            "|split|p4",
            "|-damage|p4a: Pidgeot|90/100",
            "|-damage|p4a: Pidgeot|80/100"
        )
        val replay = BattleSession().apply { setReplayMode(true) }
        val spectator = BattleSession().apply { setSpectatorMode(true) }
        val livePlayer = BattleSession().apply { setLocalUsername("RED") }

        listOf(replay, spectator, livePlayer).forEach { it.applyProtocolPacket(packet) }

        fun activeHpBySlot(session: BattleSession) =
            (session.playerActiveCombatants() + session.opponentActiveCombatants()).associate { it.slot to it.hp }

        val sharedHp = mapOf("p1a" to "80/100", "p2a" to "80/100", "p3a" to "80/100", "p4a" to "80/100")

        assertEquals(sharedHp, activeHpBySlot(replay))
        assertEquals(sharedHp, activeHpBySlot(spectator))
        assertEquals(
            mapOf("p1a" to "90/100", "p2a" to "80/100", "p3a" to "80/100", "p4a" to "80/100"),
            activeHpBySlot(livePlayer)
        )
    }

    @Test
    fun illusionRevealRemovesTheDisguiseSpeciesFromTheRevealedPokemonIndex() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|poke|p2|Pikachu, L50|",
                "|poke|p2|Pikachu, L50|",
                "|switch|p2a: Pikachu|Pikachu, L50|100/100",
                "|replace|p2a: Zoroark|Zoroark, L50|100/100",
                "|switch|p2a: Pikachu|Pikachu, L50|100/100"
            )
        )

        assertEquals(listOf("Zoroark", "Pikachu"), session.opponentPartyDetails().map { it.species })
        assertEquals("Pikachu", session.opponentActiveCombatants().single().species)
    }

    @Test
    fun illusionRevealUpdatesThePlayerPartyIdentityBeforeTheDisguiseSpeciesReturns() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|poke|p1|Pikachu, L50|",
                "|poke|p1|Pikachu, L50|",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|replace|p1a: Zoroark|Zoroark, L50|100/100",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100"
            )
        )

        assertEquals(listOf("Zoroark", "Pikachu"), session.team())
        assertEquals(listOf("Zoroark", "Pikachu"), session.playerPartyDetails().map { it.species })
        assertEquals("Pikachu", session.playerActiveCombatants().single().species)
    }

    @Test
    fun keepsTheViewerSideAsThePlayerWhenTheViewerIsP2() {
        val session = BattleSession().apply { setLocalUsername("OPPONENT") }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Dragonite|Dragonite, L50|100/100",
                "|switch|p2a: Iron Valiant|Iron Valiant, L50|100/100"
            )
        )

        assertEquals("p2", session.battlePlayerSlot())
        assertEquals("Iron Valiant", session.playerActiveCombatants().single().species)
        assertEquals("Dragonite", session.opponentActiveCombatants().single().species)
    }

    @Test
    fun formatsOfficialNumericSwapAsCenterMovement() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }
        session.applyProtocolPacket(
            listOf(
                "|gametype|triples",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1c: Dragapult|Dragapult, L50|100/100",
                "|swap|p1c: Dragapult|1"
            )
        )

        assertEquals(listOf("p1a", "p1b"), session.playerActiveCombatants().map { it.slot })
        assertEquals("Dragapult", session.playerActiveCombatants().single { it.slot == "p1b" }.name)
        assertEquals("Dragapult moved to the center!", session.battleLog().last())
    }

    @Test
    fun formatsOfficialPokemonSwapAndUpdatesOpponentPositions() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }
        session.applyProtocolPacket(
            listOf(
                "|gametype|doubles",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p2a: Rotom|Rotom-Wash, L50|100/100",
                "|switch|p2b: Garchomp|Garchomp, L50|100/100",
                "|swap|p2a: Rotom|p2b: Garchomp"
            )
        )

        assertEquals("Garchomp", session.opponentActiveCombatants().single { it.slot == "p2a" }.name)
        assertEquals("Rotom", session.opponentActiveCombatants().single { it.slot == "p2b" }.name)
        assertEquals("The opposing Rotom and the opposing Garchomp switched places!", session.battleLog().last())
    }

    @Test
    fun logsOfficialWithdrawalsBeforeNormalSwitchIns() {
        val session = switchTranscriptSession(
            "|switch|p1a: Rotom|Rotom-Wash, L50|100/100",
            "|switch|p2a: Eevee|Eevee, L50|100/100",
            "|switch|p1a: Mimikyu|Mimikyu, L50|100/100",
            "|switch|p2a: Snorlax|Snorlax, L50|100/100"
        )

        assertEquals(
            listOf(
                "Rotom, come back!",
                "Go! Mimikyu!",
                "OPPONENT withdrew Eevee!",
                "OPPONENT sent out Snorlax!"
            ),
            session.battleLog().takeLast(4)
        )
    }

    @Test
    fun formatsForcedDragWithoutAWithdrawalOrTrainerSendOutLine() {
        val session = switchTranscriptSession(
            "|switch|p2a: Eevee|Eevee, L50|100/100",
            "|drag|p2a: Garchomp|Garchomp, L50|100/100"
        )

        assertEquals("Garchomp was dragged out!", session.battleLog().last())
        assertFalse(session.battleLog().contains("OPPONENT withdrew Eevee!"))
        assertFalse(session.battleLog().contains("OPPONENT sent out Garchomp!"))
    }

    @Test
    fun omitsWithdrawLinesForSwitchesThatDoNotAnnounceAWithdrawal() {
        val switchSources = listOf("Baton Pass", "Z-Baton Pass", "Shed Tail", "Teleport")
        switchSources.forEach { source ->
            val session = switchTranscriptSession(
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|switch|p2a: Snorlax|Snorlax, L50|100/100|[from]move: $source"
            )

            assertEquals("OPPONENT sent out Snorlax!", session.battleLog().last())
            assertFalse(session.battleLog().contains("OPPONENT withdrew Eevee!"))
        }

        val relayRaceSession = switchTranscriptSession(
            "|tier|[Gen 9] Relay Race",
            "|switch|p2a: Eevee|Eevee, L50|100/100",
            "|switch|p2a: Snorlax|Snorlax, L50|100/100"
        )

        assertEquals("OPPONENT sent out Snorlax!", relayRaceSession.battleLog().last())
        assertFalse(relayRaceSession.battleLog().contains("OPPONENT withdrew Eevee!"))
    }

    @Test
    fun doesNotLogAWithdrawalWhenAReplacementFollowsAFaint() {
        val session = switchTranscriptSession(
            "|switch|p2a: Eevee|Eevee, L50|100/100",
            "|faint|p2a: Eevee",
            "|switch|p2a: Snorlax|Snorlax, L50|100/100"
        )

        assertEquals("OPPONENT sent out Snorlax!", session.battleLog().last())
        assertFalse(session.battleLog().contains("OPPONENT withdrew Eevee!"))
    }

    @Test
    fun ignoresSwapPositionsOutsideTheBattleActiveField() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }
        session.applyProtocolPacket(
            listOf(
                "|gametype|singles",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p2a: Garchomp|Garchomp, L50|100/100",
                "|swap|p1a: Incineroar|1"
            )
        )

        assertEquals(listOf("p1a"), session.playerActiveCombatants().map { it.slot })
        assertEquals("Incineroar", session.playerPokemon)
    }

    @Test
    fun ignoresNumericSwapIntoFaintedNonCenterPosition() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }
        session.applyProtocolPacket(
            listOf(
                "|gametype|doubles",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Incineroar|Incineroar, L50|0 fnt",
                "|switch|p1b: Mimikyu|Mimikyu, L50|100/100",
                "|swap|p1b: Mimikyu|0"
            )
        )

        assertEquals("Incineroar", session.playerActiveCombatants().single { it.slot == "p1a" }.name)
        assertEquals("Mimikyu", session.playerActiveCombatants().single { it.slot == "p1b" }.name)
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
    fun preservesShowdownLogLineBreaksAndInlineMarkup() {
        val session = BattleSession()

        session.appendShowdownBattleLog("<strong>Samurott</strong> used <strong>Ceaseless Edge</strong>!<br />  A critical hit!")

        assertEquals(
            listOf("Samurott used Ceaseless Edge!", "A critical hit!"),
            session.showdownBattleLog()
        )
        assertEquals(
            "**Samurott** used **Ceaseless Edge**!",
            session.battleFeedMarkupFor("Samurott used Ceaseless Edge!")
        )
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
        assertTrue(session.battleLog().contains("Mewtwo is fast asleep."))
        assertTrue(session.battleLog().any { it.contains("avoided the attack") })
        assertTrue(session.battleLog().any { it.contains("Warning: The battle is nearing its turn limit.") })
    }

    @Test
    fun formatsOfficialWeatherTerrainAndFieldAnnouncements() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-weather|RainDance",
                "|-weather|RainDance|[upkeep]",
                "|-weather|none",
                "|-fieldstart|move: Electric Terrain",
                "|-fieldend|move: Electric Terrain",
                "|-fieldstart|move: Gravity",
                "|-fieldend|move: Gravity"
            )
        )

        assertEquals(
            listOf(
                "It started to rain!",
                "(Rain continues to fall.)",
                "The rain stopped.",
                "An electric current ran across the battlefield!",
                "The electricity disappeared from the battlefield.",
                "Gravity intensified!",
                "Gravity returned to normal!"
            ),
            session.battleLog().takeLast(7)
        )
    }

    @Test
    fun formatsOfficialSideConditionAnnouncements() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }

        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|-sidestart|p1: ADRIAN|move: Stealth Rock",
                "|-sidestart|p2: OPPONENT|move: Reflect",
                "|-sidestart|p1: ADRIAN|move: Sticky Web",
                "|-sideend|p1: ADRIAN|move: Sticky Web",
                "|-sidestart|p2: OPPONENT|move: G-Max Cannonade",
                "|-sideend|p2: OPPONENT|move: G-Max Cannonade",
                "|-sidestart|p1: ADRIAN|move: Spikes|[silent]"
            )
        )

        assertEquals(
            listOf(
                "Pointed stones float in the air around your team!",
                "Reflect made the opposing team stronger against physical moves!",
                "A sticky web has been laid out on the ground around your team!",
                "The sticky web has disappeared from the ground around your team!",
                "(G-Max Cannonade started on the opposing team!)",
                "(G-Max Cannonade ended on the opposing team!)"
            ),
            session.battleLog().takeLast(6)
        )
        assertFalse(session.battleLog().any { it.contains("Spikes") })
    }

    @Test
    fun formatsOfficialProtocolAnnouncements() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|switch|p2a: Magikarp|Magikarp, L1|11/11",
                "|-fieldactivate|move: Fairy Lock",
                "|-fail|p1a: Mewtwo|move: Protect",
                "|-notarget|p1a: Mewtwo",
                "|-miss|p1a: Mewtwo|p2a: Magikarp",
                "|-miss|p1a: Mewtwo",
                "|-immune|p2a: Magikarp",
                "|-immune",
                "|-combine|p1a: Mewtwo",
                "|-nothing",
                "|-zpower|p1a: Mewtwo",
                "|-zbroken|p1a: Mewtwo",
                "|-waiting|p1a: Mewtwo|p2a: Magikarp"
            )
        )

        assertEquals(
            listOf(
                "(Fairy Lock started!)",
                "But it failed!",
                "But there was no target...",
                "The opposing Magikarp avoided the attack!",
                "Mewtwo's attack missed!",
                "It doesn't affect the opposing Magikarp...",
                "But it had no effect!",
                "The two moves have become one! It's a combined move!",
                "Splash activated.",
                "Mewtwo surrounded itself with its Z-Power!",
                "Mewtwo couldn't fully protect itself and got hurt!",
                "Mewtwo is waiting for the opposing Magikarp's move..."
            ),
            session.battleLog().takeLast(12)
        )
    }

    @Test
    fun formatsOfficialEffectVariantsAndMultiplierAnnouncements() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }
        val initialLogSize = session.battleLog().size

        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|turn|1",
                "|-supereffective|p2a: Gholdengo|2",
                "|-resisted|p2a: Gholdengo|2",
                "|-center",
                "|-hitcount|p2a: Gholdengo|1",
                "|-hitcount|p2a: Gholdengo|3",
                "|-start|p1a: Mewtwo|typechange|WATER",
                "|-end|p1a: Mewtwo|typechange",
                "|-terastallize|p1a: Mewtwo|FIRE",
                "|-start|p1a: Mewtwo|Dynamax",
                "|-end|p1a: Mewtwo|Dynamax",
                "|-candynamax|p1",
                "|-block|p1a: Mewtwo|Dynamax"
            )
        )

        assertEquals(
            listOf(
                "Turn 1.",
                "It's extremely effective!",
                "It's mostly ineffective...",
                "Automatic center!",
                "The Pokémon was hit 1 time!",
                "The Pokémon was hit 3 times!",
                "Mewtwo transformed into the Water type!",
                "Mewtwo was freed from typechange!",
                "(Mewtwo has Terastallized into the Fire-type!)",
                "(Mewtwo's Dynamax!)",
                "(Mewtwo returned to normal!)",
                "Dynamax Energy gathered around ADRIAN!",
                "The move was blocked by the power of Dynamax!"
            ),
            session.battleLog().drop(initialLogSize)
        )
    }

    @Test
    fun formatsOfficialTransformFailureSpreadAndSilentAnnouncements() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|switch|p2a: Magikarp|Magikarp, L1|11/11",
                "|-fail|p1a: Mewtwo|unboost|atk",
                "|-fail|p1a: Mewtwo|heal",
                "|-fail|p1a: Mewtwo|brn",
                "|-fail|p1a: Mewtwo|dynamax",
                "|-supereffective|p2a: Magikarp|2|[spread]",
                "|-resisted|p2a: Magikarp|[spread]",
                "|-crit|p2a: Magikarp|[spread]",
                "|-ohko|p2a: Magikarp|[silent]",
                "|-transform|p1a: Mewtwo|p2a: Magikarp"
            )
        )

        assertTrue(session.battleLog().contains("Mewtwo's Attack was not lowered!"))
        assertTrue(session.battleLog().contains("Mewtwo's HP is full!"))
        assertTrue(session.battleLog().contains("Mewtwo is already burned!"))
        assertTrue(session.battleLog().contains("Mewtwo shook its head. It seems like it can't use this move..."))
        assertTrue(session.battleLog().contains("It's extremely effective on the opposing Magikarp!"))
        assertTrue(session.battleLog().contains("It's not very effective on the opposing Magikarp."))
        assertTrue(session.battleLog().contains("A critical hit on the opposing Magikarp!"))
        assertTrue(session.battleLog().contains("Mewtwo transformed!"))
        assertFalse(session.battleLog().contains("It's a one-hit KO!"))
    }

    @Test
    fun formatsOfficialMegaAndPrimalAnnouncements() {
        val session = BattleSession().apply {
            setLocalUsername("ADRIAN")
            applyProtocolPacket(
                listOf(
                    "|player|p1|ADRIAN||",
                    "|player|p2|OPPONENT||",
                    "|switch|p1a: Charizard|Charizard, L50|100/100",
                    "|switch|p2a: Kyogre|Kyogre, L50|100/100"
                )
            )
        }

        session.applyProtocolPacket(
            listOf(
                "|-mega|p1a: Charizard|Charizard|Charizardite X",
                "|-primal|p2a: Kyogre|Blue Orb"
            )
        )

        assertTrue(session.battleLog().contains("Charizard's Charizardite X is reacting to the Key Stone!"))
        assertTrue(session.battleLog().contains("Charizard has Mega Evolved into Mega Charizard!"))
        assertTrue(session.battleLog().contains("The opposing Kyogre's Primal Reversion! It reverted to its primal state!"))
        assertEquals("Charizardite X", session.playerDetails().item)
        assertEquals("Blue Orb", session.opponentDetails().item)
    }

    @Test
    fun keepsOpponentPerspectiveOutOfMegaSpeciesName() {
        val session = BattleSession().apply {
            setLocalUsername("Red")
            applyProtocolPacket(
                listOf(
                    "|player|p1|Red||",
                    "|player|p2|Blue||",
                    "|switch|p2a: Blazer|Charizard, L50|100/100"
                )
            )
        }

        session.applyProtocolPacket(listOf("|-mega|p2a: Blazer|Charizardite X"))

        assertTrue(session.battleLog().contains("The opposing Blazer has Mega Evolved into Mega Charizard!"))
    }

    @Test
    fun keepsSilentProtocolStateUpdatesOutOfTheUserFacingBattleFeed() {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }

        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|-status|p1a: Pikachu|psn|[silent]",
                "|-boost|p1a: Pikachu|atk|1|[silent]",
                "|-fail|p1a: Pikachu|move: Protect|[silent]",
                "|-weather|RainDance|[silent]",
                "|-fieldstart|move: Electric Terrain|[silent]"
            )
        )

        assertEquals("PSN", session.playerDetails().condition)
        assertEquals("RainDance", session.battleInfo().weather)
        assertEquals("Electric Terrain", session.battleInfo().terrain)
        assertFalse(session.battleLog().any { it.contains("status", true) })
        assertFalse(session.battleLog().any { it.contains("rose", true) })
        assertFalse(session.battleLog().any { it.contains("failed", true) })
        assertFalse(session.battleLog().contains("It started to rain!"))
        assertFalse(session.battleLog().contains("An electric current ran across the battlefield!"))

        session.applyProtocolLine("|-curestatus|p1a: Pikachu|brn|[silent]")
        assertFalse(session.battleLog().contains("Pikachu's burn was healed!"))
    }

    @Test
    fun formatsFailureEventsWithoutAnOptionalEffectName() {
        val session = BattleSession()

        session.applyProtocolLine("|-fail|p1a: Plusle")

        assertTrue(session.battleLog().contains("But it failed!"))
    }

    @Test
    fun formatsRechargeAnnouncementsLikeShowdown() {
        val session = BattleSession()

        session.applyProtocolLine("|-mustrecharge|p1a: Hyper Beam")
        session.applyProtocolLine("|cant|p1a: Hyper Beam|recharge")

        assertEquals(1, session.battleLog().count { it == "Hyper Beam must recharge!" })
    }

    @Test
    fun preservesOptionalRatedAndTimerMessages() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|rated|Tournament battle",
                "|inactiveoff|The battle timer is off."
            )
        )

        assertTrue(session.battleLog().contains("Tournament battle"))
        assertTrue(session.battleLog().contains("The battle timer is off."))
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
                "|-ability|p1a: Mewtwo|Pressure",
                "|-sethp|p1a: Mewtwo|70/100 brn",
                "|-endability|p1a: Mewtwo",
                "|-ability|p2a: Magikarp|Swift Swim",
                "|-transform|p1a: Mewtwo|p2a: Magikarp",
                "|-hitcount|p1a: Mewtwo|3",
                "|-waiting|p1a: Mewtwo|p2a: Magikarp",
                "|-zpower|p1a: Mewtwo",
                "|-cureteam|p1a: Mewtwo"
            )
        )

        assertEquals("Mewtwo", session.playerPokemon)
        assertEquals("70/100", session.playerHp)
        assertEquals("READY", session.playerCondition)
        assertEquals("Swift Swim", session.playerDetails().ability)
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
        assertTrue(session.battleLog().any { it.contains("Gliscor protected itself!") })
        assertTrue(session.battleLog().any { it.contains("Gholdengo's Ability is protected by the effects of its Ability Shield!") })
        assertTrue(session.battleLog().any { it.contains("Gliscor protected itself with its Protective Pads!") })
    }

    @Test
    fun formatsOfficialProtectionAndMoveBlockAnnouncements() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Gholdengo|Gholdengo, L50|100/100",
                "|switch|p2a: Gliscor|Gliscor, L50|100/100",
                "|-block|p1a: Gholdengo|Quick Guard",
                "|-block|p2a: Gliscor|Wide Guard",
                "|-block|p1a: Gholdengo|Crafty Shield",
                "|-block|p2a: Gliscor|Mat Block|move: Earthquake",
                "|-block|p1a: Gholdengo|item: Safety Goggles|move: Spore",
                "|-block|p2a: Gliscor|Misty Terrain",
                "|-block|p1a: Gholdengo|Safeguard",
                "|-block|p2a: Gliscor|Ingrain",
                "|-block|p1a: Gholdengo|ability: Aroma Veil",
                "|-block|p2a: Gliscor|ability: Flower Veil",
                "|-block|p1a: Gholdengo|ability: Sweet Veil",
                "|-block|p2a: Gliscor|ability: Telepathy",
                "|-block|p1a: Gholdengo|ability: Sticky Hold",
                "|-block|p2a: Gliscor|ability: Suction Cups",
                "|-block|p1a: Gholdengo|ability: Disguise"
            )
        )

        assertTrue(session.battleLog().any { it.contains("Quick Guard protected Gholdengo!") })
        assertTrue(session.battleLog().any { it.contains("Wide Guard protected the opposing Gliscor!") })
        assertTrue(session.battleLog().any { it.contains("Crafty Shield protected Gholdengo!") })
        assertTrue(session.battleLog().any { it.contains("Earthquake was blocked by the kicked-up mat!") })
        assertTrue(session.battleLog().any { it.contains("Gholdengo is not affected by Spore thanks to its Safety Goggles!") })
        assertTrue(session.battleLog().any { it.contains("Gliscor surrounds itself with a protective mist!") })
        assertTrue(session.battleLog().any { it.contains("Gholdengo is protected by Safeguard!") })
        assertTrue(session.battleLog().any { it.contains("Gliscor is anchored in place with its roots!") })
        assertTrue(session.battleLog().any { it.contains("Gholdengo is protected by an aromatic veil!") })
        assertTrue(session.battleLog().any { it.contains("Gliscor surrounded itself with a veil of petals!") })
        assertTrue(session.battleLog().any { it.contains("Gholdengo can't fall asleep due to a veil of sweetness!") })
        assertTrue(session.battleLog().any { it.contains("Gliscor can't be hit by attacks from its ally Pokémon!") })
        assertTrue(session.battleLog().any { it.contains("Gholdengo's item cannot be removed!") })
        assertTrue(session.battleLog().any { it.contains("Gliscor is anchored in place with its suction cups!") })
        assertTrue(session.battleLog().any { it.contains("Its disguise served it as a decoy!") })
        assertEquals(listOf("Wide Guard", "Mat Block"), session.opponentActiveCombatants().single().turnEffects)
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
    fun keepsOfficialAbilityAndItemAnnouncementsInTheBattleFeed() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Gholdengo|Gholdengo, L50|100/100",
                "|-ability|p1a: Gholdengo|Good as Gold",
                "|-item|p1a: Gholdengo|Air Balloon",
                "|-ability|p1a: Gholdengo|Klutz|[from] ability: Skill Swap",
                "|-item|p1a: Gholdengo|Leftovers|[from] move: Trick",
                "|-ability|p1a: Gholdengo|Unburden|[silent]",
                "|-item|p1a: Gholdengo|Choice Scarf|[silent]"
            )
        )

        assertEquals("Unburden", session.playerDetails().ability)
        assertEquals("Choice Scarf", session.playerDetails().item)
        assertTrue(session.battleLog().contains("Gholdengo's Good as Gold activated."))
        assertTrue(session.battleLog().contains("Gholdengo's Air Balloon activated."))
        assertTrue(session.battleLog().contains("Gholdengo's ability became Klutz."))
        assertTrue(session.battleLog().contains("Gholdengo obtained Leftovers."))
        assertFalse(session.battleLog().any { it.contains("Unburden") })
        assertFalse(session.battleLog().any { it.contains("Choice Scarf") })
    }

    @Test
    fun formatsStatAnnouncementsLikeTheShowdownBattleLog() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-boost|p1a: Mewtwo|spa|2",
                "|-unboost|p1a: Mewtwo|atk|2",
                "|-boost|p1a: Mewtwo|spe|1|[silent]"
            )
        )

        assertTrue(session.battleLog().contains("Mewtwo's Special Attack rose sharply."))
        assertTrue(session.battleLog().contains("Mewtwo's Attack fell harshly."))
        assertFalse(session.battleLog().any { it.contains("Speed") })
    }

    @Test
    fun formatsStatusAnnouncementsLikeTheShowdownBattleLog() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-status|p1a: Mewtwo|brn",
                "|-status|p2a: Magikarp|par",
                "|-status|p1a: Mewtwo|tox"
            )
        )

        assertTrue(session.battleLog().contains("Mewtwo was burned!"))
        assertTrue(session.battleLog().contains("The opposing Magikarp is paralyzed, so it may be unable to move!"))
        assertTrue(session.battleLog().contains("Mewtwo was badly poisoned!"))
    }

    @Test
    fun formatsOfficialStatusCuresAndCantReasons() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-curestatus|p1a: Mewtwo|brn",
                "|-curestatus|p1a: Mewtwo|frz",
                "|-curestatus|p1a: Mewtwo|par",
                "|-curestatus|p1a: Mewtwo|psn",
                "|-curestatus|p1a: Mewtwo|slp",
                "|-curestatus|p1a: Mewtwo|brn|[from] item: Lum Berry",
                "|-curestatus|p1a: Mewtwo|frz|[from] move: Flamethrower",
                "|cant|p1a: Mewtwo|slp",
                "|cant|p1a: Mewtwo|frz",
                "|cant|p1a: Mewtwo|par",
                "|cant|p1a: Mewtwo|flinch",
                "|cant|p1a: Mewtwo|recharge",
                "|cant|p1a: Mewtwo|gravity|Thunder Wave"
            )
        )

        assertTrue(session.battleLog().contains("Mewtwo's burn was healed!"))
        assertTrue(session.battleLog().contains("Mewtwo thawed out!"))
        assertTrue(session.battleLog().contains("Mewtwo was cured of paralysis!"))
        assertTrue(session.battleLog().contains("Mewtwo was cured of its poisoning!"))
        assertTrue(session.battleLog().contains("Mewtwo woke up!"))
        assertTrue(session.battleLog().contains("Mewtwo's Lum Berry healed its burn!"))
        assertTrue(session.battleLog().contains("Mewtwo's Flamethrower melted the ice!"))
        assertTrue(session.battleLog().contains("Mewtwo is fast asleep."))
        assertTrue(session.battleLog().contains("Mewtwo is frozen solid!"))
        assertTrue(session.battleLog().contains("Mewtwo is paralyzed! It can't move!"))
        assertTrue(session.battleLog().contains("Mewtwo flinched and couldn't move!"))
        assertEquals(1, session.battleLog().count { it == "Mewtwo must recharge!" })
        assertTrue(session.battleLog().contains("Mewtwo can't use Thunder Wave because of gravity!"))
    }

    @Test
    fun appliesOfficialBoostTransferDirectionAndAnnouncements() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Mewtwo|Mewtwo, L50|100/100",
                "|switch|p1b: Mimikyu|Mimikyu, L50|100/100",
                "|-boost|p1a: Mewtwo|spa|2",
                "|-copyboost|p1a: Mewtwo|p1b: Mimikyu",
                "|-invertboost|p1b: Mimikyu",
                "|-clearnegativeboost|p1b: Mimikyu",
                "|-unboost|p1a: Mewtwo|atk|2",
                "|-restoreboost|p1a: Mewtwo"
            )
        )

        assertTrue(session.battleInfo().playerBoosts.containsKey("spa"))
        assertTrue(session.battleLog().any { it.contains("Mimikyu copied stat changes from Mewtwo.") })
        assertTrue(session.battleLog().any { it.contains("Mimikyu's stat changes were inverted.") })
        assertTrue(session.battleLog().any { it.contains("Mimikyu's negative stat changes were removed.") })
        assertTrue(session.battleLog().any { it.contains("Mewtwo restored its lowered stats.") })
    }

    @Test
    fun suppressesSilentStatResetAnnouncements() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-clearallboost|[silent]",
                "|-clearboost|p1a: Mewtwo|[silent]",
                "|-copyboost|p1a: Mewtwo|p1b: Mimikyu|[silent]",
                "|-invertboost|p1b: Mimikyu|[silent]",
                "|-swapboost|p1a: Mewtwo|p1b: Mimikyu|def|[silent]"
            )
        )

        assertFalse(session.battleLog().any { message ->
            message.contains("stat changes", true) ||
                message.contains("stat changes were inverted", true) ||
                message.contains("All stat changes", true)
        })

        session.applyProtocolLine("|-clearallboost")

        assertTrue(session.battleLog().contains("All stat changes were reset."))
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
        assertTrue(session.battleLog().contains("The two moves have become one! It's a combined move!"))
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
    fun restoresTerastallizationFromSwitchInDetailsAfterSwitchingOut() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
            setPokemonTypeResolver(
                mapOf(
                    "Pikachu" to listOf("ELECTRIC"),
                    "Eevee" to listOf("NORMAL"),
                    "Gengar" to listOf("GHOST")
                )::get
            )
        }

        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|poke|p1|Pikachu|",
                "|poke|p1|Eevee|",
                "|poke|p2|Gengar|",
                "|poke|p2|Haunter|",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|switch|p2a: Gengar|Gengar, L50|100/100",
                "|-terastallize|p1a: Pikachu|WATER",
                "|-terastallize|p2a: Gengar|FIRE",
                "|switch|p1a: Eevee|Eevee, L50|100/100",
                "|switch|p2a: Haunter|Haunter, L50|100/100",
                "|switch|p1a: Pikachu|Pikachu, L50, tera:WATER|100/100",
                "|switch|p2a: Gengar|Gengar, L50, tera:FIRE|100/100"
            )
        )

        assertEquals("Pikachu", session.playerActiveCombatants().single().name)
        assertEquals(listOf("WATER"), session.playerActiveCombatants().single().types)
        assertEquals("Gengar", session.opponentActiveCombatants().single().name)
        assertEquals(listOf("FIRE"), session.opponentActiveCombatants().single().types)
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

        assertEquals("Mewtwo", session.playerPokemon)
        assertEquals("Mewtwo", session.playerDetails().name)
        assertEquals("Dragapult", session.playerDetails().species)
        assertEquals(listOf("DRAGON", "GHOST"), session.playerDetails().types)
    }

    @Test
    fun transformsIntoTheTargetSpeciesWithoutLosingEitherNicknameOrRevealedDetails() {
        val session = BattleSession()
        session.setPokemonTypeResolver(
            mapOf(
                "Mewtwo" to listOf("PSYCHIC"),
                "Dragapult" to listOf("DRAGON", "GHOST")
            )::get
        )
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Copycat|Mewtwo, L50|100/100",
                "|switch|p2a: Phantom|Dragapult, L50|100/100",
                "|-ability|p2a: Phantom|Infiltrator",
                "|-transform|p1a: Copycat|p2a: Phantom"
            )
        )

        assertEquals("Copycat", session.playerPokemon)
        assertEquals("Copycat", session.playerDetails().name)
        assertEquals("Dragapult", session.playerDetails().species)
        assertEquals(listOf("DRAGON", "GHOST"), session.playerDetails().types)
        assertEquals("Infiltrator", session.playerDetails().ability)
    }

    @Test
    fun temporaryTransformDoesNotReplaceThePokemonSpeciesAfterSwitchingOut() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|poke|p1|Ditto|",
                "|poke|p1|Pikachu|",
                "|poke|p2|Dragapult|",
                "|switch|p1a: Copycat|Ditto, L50|100/100",
                "|switch|p2a: Phantom|Dragapult, L50|100/100",
                "|-ability|p2a: Phantom|Infiltrator",
                "|-transform|p1a: Copycat|p2a: Phantom",
                "|move|p1a: Copycat|Shadow Ball|p2a: Phantom",
                "|switch|p1a: Bolt|Pikachu, L50|100/100"
            )
        )

        val spriteRequest = BattleSpriteRequests.active(
            session.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request

        assertEquals(listOf("Ditto", "Pikachu"), session.playerPartyDetails().map { it.species })
        assertEquals(listOf("Copycat", "Bolt"), session.team())
        assertEquals("Pikachu", spriteRequest.species)
        assertTrue(session.battleLog().contains("Copycat used Shadow Ball!"))
    }

    @Test
    fun temporaryTransformKeepsThePokemonIdentityAndLevel() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|poke|p1|Ditto|",
                "|poke|p2|Dragapult|",
                "|switch|p1a: Ditto|Ditto, L50|100/100",
                "|switch|p2a: Phantom|Dragapult, L80, M|100/100",
                "|-transform|p1a: Ditto|p2a: Phantom"
            )
        )

        val active = session.playerActiveCombatants().single()

        assertEquals("Ditto", active.name)
        assertEquals("Dragapult", active.species)
        assertEquals("50", active.level)
        assertEquals("", active.gender)
        assertEquals("Ditto", session.playerDetails().name)
        assertEquals("50", session.playerDetails().level)
    }

    @Test
    fun temporaryOpponentTransformDoesNotReplaceThePokemonSpeciesAfterSwitchingOut() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|clearpoke",
                "|poke|p1|Dragapult|",
                "|poke|p2|Ditto|",
                "|poke|p2|Eevee|",
                "|switch|p1a: Phantom|Dragapult, L50|100/100",
                "|switch|p2a: Ditto|Ditto, L50|100/100",
                "|-transform|p2a: Ditto|p1a: Phantom",
                "|switch|p2a: Eevee|Eevee, L50|100/100"
            )
        )

        val spriteRequest = BattleSpriteRequests.active(
            session.opponentActiveCombatants(),
            BattleSpriteSide.OPPONENT,
            BattleSession.SpriteStyle.MODERN_3D
        ).single().request

        assertEquals(listOf("Ditto", "Eevee"), session.opponentPartyDetails().map { it.species })
        assertEquals("Eevee", spriteRequest.species)
        assertTrue(session.battleLog().contains("The opposing Ditto transformed!"))
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
                "|faint|p1b: Rotom-Wash"
            )
        )

        assertEquals("Incineroar", session.playerPokemon)
        assertEquals(listOf("FIRE", "DARK"), session.playerDetails().types)
        assertEquals("Rotom-Wash", session.teamMemberDetails(4).name)
        assertEquals("Rotom-Frost", session.teamMemberDetails(4).species)
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

    private fun switchTranscriptSession(vararg events: String): BattleSession {
        val session = BattleSession().apply { setLocalUsername("ADRIAN") }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||"
            ) + events.toList()
        )
        return session
    }
}
