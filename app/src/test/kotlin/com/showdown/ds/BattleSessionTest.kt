package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSessionTest {
    @Test
    fun requestPopulatesMovesAndResetsFocus() {
        val session = BattleSession()
        session.focusMove(3)
        session.setMoveInfoResolver { name ->
            if (name == "Moonblast") BattleSession.MoveInfo("95", "100") else null
        }

        session.applyProtocolLine("|request|{\"active\":[{\"moves\":[{\"move\":\"Moonblast\",\"type\":\"Fairy\",\"pp\":15},{\"move\":\"Thunderbolt\",\"type\":\"Electric\",\"pp\":24}]}]}")

        assertEquals(BattleSession.Panel.MOVES, session.panel)
        assertEquals(0, session.focusedMove)
        assertEquals(2, session.moves().size)
        assertEquals("Moonblast", session.moves()[0].name)
        assertEquals("FAIRY", session.moves()[0].type)
        assertEquals("95", session.moves()[0].power)
        assertEquals("100", session.moves()[0].accuracy)
    }

    @Test
    fun requestUsesDashesForMovesWithoutNumericAccuracy() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Swift\",\"pp\":20,\"accuracy\":true},{\"move\":\"Thunderbolt\",\"pp\":15,\"accuracy\":85}]}]}"
        )

        assertEquals("—", session.moves()[0].accuracy)
        assertEquals("85", session.moves()[1].accuracy)
    }

    @Test
    fun waitingRequestsRemoveStaleMoveControls() {
        val session = BattleSession()
        session.applyProtocolLine("|request|{\"active\":[{\"canZMove\":[{}],\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15}]}]}")
        assertTrue(session.availableGimmicks().isNotEmpty())

        session.applyProtocolLine("|request|{\"wait\":true}")

        assertTrue(session.moves().isEmpty())
        assertTrue(session.availableGimmicks().isEmpty())
        assertFalse(session.decisionAvailable)
        assertEquals("Waiting for the other player…", session.status)
    }

    @Test
    fun disabledMovesCannotBeSubmitted() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine("|request|{\"rqid\":9,\"active\":[{\"moves\":[{\"move\":\"Splash\",\"pp\":0,\"disabled\":true}]}]}")

        session.confirmSelection()

        assertTrue(decisions.isEmpty())
        assertTrue(session.decisionAvailable)
        assertEquals("Splash is disabled.", session.status)
    }

    @Test
    fun failedMoveSubmissionRestoresTheDecision() {
        val session = BattleSession()
        session.applyProtocolLine("|request|{\"rqid\":9,\"active\":[{\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15}]}]}")

        session.confirmSelection()
        assertFalse(session.decisionAvailable)

        session.handleDecisionSendFailure()

        assertTrue(session.decisionAvailable)
        assertEquals(BattleSession.Panel.MOVES, session.panel)
        assertEquals("Connection unavailable. Choose a move again.", session.status)
    }

    @Test
    fun lobbyChatAndPrivateMessagesEnterActivity() {
        val session = BattleSession()

        session.applyLobbyChat(listOf("|c|MISTY|Hello", "|c:|123|MISTY|Timestamped hello", "|pm|GARY|ADRIAN|Want to battle?"))

        assertTrue(session.chatMessages().contains("[MISTY] Hello"))
        assertTrue(session.chatMessages().contains("[MISTY] Timestamped hello"))
        assertTrue(session.chatMessages().contains("[PM GARY] Want to battle?"))
        assertTrue(session.activityMessages().last().contains("Want to battle?"))
    }

    @Test
    fun failedLocalChatCanBeRemovedWithoutTouchingEarlierMessages() {
        val session = BattleSession()
        session.sendChat("gl hf")

        session.removeLocalChat("gl hf")

        assertFalse(session.chatMessages().contains("[You] gl hf"))
        assertFalse(session.activityMessages().contains("[You] gl hf"))
        assertTrue(session.chatMessages().isNotEmpty())
    }

    @Test
    fun multiActiveRequestsCollectAllMovesBeforeSubmitting() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"rqid\":12,\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]},{\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}]}"
        )

        session.confirmSelection()

        assertTrue(decisions.isEmpty())
        assertEquals("Choose a move for active Pokémon 2/2", session.status)
        session.confirmSelection()

        assertEquals(listOf("/choose move 1, move 1|12"), decisions)
        assertEquals(false, session.decisionAvailable)
    }

    @Test
    fun multiActiveRequestsPassInactiveSlotsBeforeSubmittingMoves() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"rqid\":16,\"active\":[{}, {\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}]}"
        )

        assertEquals("Choose a move for active Pokémon 2/2", session.status)
        session.confirmSelection()

        assertEquals(listOf("/choose pass, move 1|16"), decisions)
    }

    @Test
    fun multiActiveRequestsAllowExplicitTargets() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"rqid\":13,\"active\":[{\"moves\":[{\"move\":\"Rock Slide\",\"pp\":10,\"target\":\"normal\"}]},{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"target\":\"self\"}]}]}"
        )

        session.confirmSelection()

        assertTrue(decisions.isEmpty())
        assertEquals(listOf("Foe 1", "Foe 2"), session.targetOptions().map { it.label })
        session.selectTargetWithTouch(1)
        session.confirmSelection()

        assertEquals(listOf("/choose move 1 2, move 1|13"), decisions)
    }

    @Test
    fun multiActiveRequestsUseOfficialAllyTargetSlots() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Helping Hand\",\"pp\":10,\"target\":\"adjacentAllyOrSelf\"}]},{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"target\":\"self\"}]}]}"
        )

        session.confirmSelection()

        assertEquals(listOf("Ally 1 (self)", "Ally 2"), session.targetOptions().map { it.label })
        assertEquals(listOf("-1", "-2"), session.targetOptions().map { it.choice })
    }

    @Test
    fun multiActiveRequestsExcludeFaintedAllyTargetSlots() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Mimikyu|Mimikyu, L50|100/100",
                "|faint|p1a: Incineroar",
                "|request|{\"active\":[{}, {\"moves\":[{\"move\":\"Helping Hand\",\"pp\":10,\"target\":\"adjacentAllyOrSelf\"}]}]}"
            )
        )

        assertEquals(listOf("Ally 2 (self)"), session.targetOptions().map { it.label })
        assertEquals(listOf("-2"), session.targetOptions().map { it.choice })
    }

    @Test
    fun multiActiveRequestsSkipFaintedFoeSlots() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|0 fnt",
                "|switch|p2b: Landorus|Landorus, L50|100/100",
                "|faint|p2a: Tapu Koko",
                "|request|{\"active\":[{\"moves\":[{\"move\":\"Rock Slide\",\"pp\":10,\"target\":\"normal\"}]},{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"target\":\"self\"}]}]}"
            )
        )

        session.confirmSelection()

        assertEquals(listOf("Foe 2"), session.targetOptions().map { it.label })
        assertEquals(listOf("2"), session.targetOptions().map { it.choice })
    }

    @Test
    fun activePokemonSwapsUpdateTheDisplayedPrimaryCombatant() {
        val session = BattleSession()
        session.setLocalUsername("ADRIAN")
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Mimikyu|Mimikyu, L50|100/100",
                "|swap|p1b|0"
            )
        )

        assertEquals(listOf("Mimikyu", "Incineroar"), session.playerActiveCombatants().map { it.name })
        assertEquals("Mimikyu", session.playerPokemon)
    }

    @Test
    fun teamPreviewBuildsTheSubmittedOrderFromIndividualSelections() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine("|request|{\"rqid\":14,\"teamPreview\":true}")

        val desiredOrder = listOf(2, 0, 1, 3, 4, 5)
        desiredOrder.forEach { index ->
            session.moveFocus(index % 3 - session.focusedTeam % 3, index / 3 - session.focusedTeam / 3)
            session.confirmSelection()
        }

        assertEquals(desiredOrder, session.teamPreviewOrder())
        assertEquals(listOf("/choose team 312456|14"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun teamPreviewBackRemovesTheLastSelectionBeforeSubmission() {
        val session = BattleSession()
        session.applyProtocolLine("|request|{\"rqid\":15,\"teamPreview\":true}")

        session.confirmSelection()
        session.goBack()

        assertTrue(session.decisionAvailable)
        assertTrue(session.teamPreviewOrder().isEmpty())
    }

    @Test
    fun protocolLinesUpdateBattleState() {
        val session = BattleSession()

        session.applyProtocolLine("|player|p1|ADRIAN")
        session.applyProtocolLine("|player|p2|GLADION")
        session.applyProtocolLine("|tier|[Gen 7] OU")
        session.applyProtocolLine("|switch|p1a: Incineroar|Incineroar, L50, M|100/100")
        session.applyProtocolLine("|switch|p2a: Tapu Koko|Tapu Koko, L50|75/100")
        session.applyProtocolLine("|turn|7")
        session.applyProtocolLine("|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko")

        assertEquals("ADRIAN", session.playerName)
        assertEquals("GLADION", session.opponentName)
        assertEquals("Incineroar", session.playerPokemon)
        assertEquals("Tapu Koko", session.opponentPokemon)
        assertEquals("75/100", session.opponentHp)
        assertEquals(7, session.turn)
        assertEquals("[Gen 7] OU", session.format)
        assertTrue(session.battleLog().last().contains("Flare Blitz"))
    }

    @Test
    fun liveProtocolEventsCanBePresentedSeparatelyFromAuthoritativeState() {
        val session = BattleSession()
        val initialEvent = session.latestBattleEvent
        val events = mutableListOf<String>()
        session.addBattleEventListener { events += it }

        session.applyProtocolPacket(listOf("|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko"))

        assertEquals(initialEvent, session.latestBattleEvent)
        assertEquals(listOf("Incineroar used Flare Blitz!"), events)
        assertTrue(session.battleLog().last().contains("Flare Blitz"))
    }

    @Test
    fun openingCombatantsEnterInProtocolOrder() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p1a: Incineroar|Incineroar, L50, M|316/316"
            )
        )

        assertTrue(session.playerEntryAtNanos - session.opponentEntryAtNanos >= BattleSceneTiming.summonDurationNanos)
        assertEquals("Go! Incineroar!", session.sendOutMessage("Incineroar", true))
        assertEquals("OPPONENT sent out Tapu Koko!", session.sendOutMessage("Tapu Koko", false))
    }

    @Test
    fun newBattleInitializationClearsPreviousBattlePresentationState() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko",
                "|-damage|p2a: Tapu Koko|25/100",
                "|c|GLADION|Good luck!"
            )
        )

        session.applyProtocolLine("|init|battle")

        assertEquals(listOf("Battle started."), session.battleLog())
        assertEquals(listOf("Battle started."), session.activityMessages())
        assertTrue(session.moves().isEmpty())
        assertFalse(session.decisionAvailable)
        assertEquals("Battle starting", session.latestBattleEvent)
    }

    @Test
    fun lobbyPreparationRemovesBattleControlsAndSelectsTheMenu() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|request|{\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}]}"
            )
        )
        session.setLiveBattleActive(true)

        session.prepareForLobby()

        assertFalse(session.isLiveBattleActive())
        assertFalse(session.isBattleFinished())
        assertFalse(session.decisionAvailable)
        assertEquals(BattleSession.Panel.MENU, session.panel)
        assertTrue(session.moves().isEmpty())
        assertEquals(listOf("No battle in progress."), session.battleLog())
        assertTrue(session.status.contains("Find a battle"))
    }

    @Test
    fun doublesKeepIndependentActiveCombatantState() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|switch|p1a: Incineroar|Incineroar, L50, M|316/316",
                "|switch|p1b: Mimikyu|Mimikyu, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|250/250",
                "|switch|p2b: Landorus|Landorus, L50, M|300/300",
                "|-damage|p1b: Mimikyu|50/100",
                "|faint|p2b: Landorus",
                "|-terastallize|p2b: Landorus|Water"
            )
        )

        assertEquals(listOf("Incineroar", "Mimikyu"), session.playerActiveCombatants().map { it.name })
        assertEquals(listOf("Tapu Koko", "Landorus"), session.opponentActiveCombatants().map { it.name })
        assertEquals("316/316", session.playerHp)
        assertEquals("50/100", session.playerActiveCombatants()[1].hp)
        assertEquals("FNT", session.opponentActiveCombatants()[1].condition)
        assertEquals(listOf("WATER"), session.opponentActiveCombatants()[1].types)
    }

    @Test
    fun controllerNavigationUsesTheLowerScreenState() {
        val session = BattleSession()

        session.moveFocus(0, 1)
        session.moveFocus(0, 1)
        session.moveFocus(0, 1)
        session.confirmSelection()
        session.cyclePanel(1)

        assertEquals(3, session.focusedMove)
        assertEquals(BattleSession.Panel.TEAM, session.panel)
        assertTrue(session.chatMessages().last().contains("/choose move 4"))
    }

    @Test
    fun controllerMovesThroughTheVerticalMoveStackWithoutSkipping() {
        val session = BattleSession()

        session.moveFocus(0, 1)
        assertEquals(1, session.focusedMove)
        session.moveFocus(0, 1)
        assertEquals(2, session.focusedMove)
        session.moveFocus(0, -1)
        assertEquals(1, session.focusedMove)
    }

    @Test
    fun controllerNavigatesEveryCommandDeckPanel() {
        val session = BattleSession()

        session.selectPanel(BattleSession.Panel.TEAM)
        session.moveFocus(1, 1)
        assertEquals(4, session.focusedTeam)
        session.selectPanel(BattleSession.Panel.ACTIVITY)
        session.moveFocus(0, 1)
        assertEquals(1, session.focusedMessage)
        session.selectPanel(BattleSession.Panel.MENU)
        session.moveFocus(0, 1)

        assertEquals(3, session.focusedMenuItem)
    }

    @Test
    fun touchRequiresASecondTapByDefaultAndCanBeDisabled() {
        val session = BattleSession()

        session.selectMoveWithTouch(2)
        assertTrue(session.touchConfirmationEnabled)
        assertEquals(2, session.focusedMove)
        assertFalse(session.chatMessages().last().contains("/choose move 3"))

        session.selectMoveWithTouch(2)
        assertTrue(session.chatMessages().last().contains("/choose move 3"))
        assertEquals("Incineroar chose Darkest Lariat.", session.latestBattleEvent)
        assertEquals("Move sent: Darkest Lariat", session.status)

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(7)
        session.confirmSelection()
        session.applyProtocolLine("|request|{\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35},{\"move\":\"Growl\",\"pp\":40}]}]}")
        session.selectMoveWithTouch(1)

        assertFalse(session.touchConfirmationEnabled)
        assertEquals(1, session.focusedMove)
        assertTrue(session.chatMessages().last().contains("/choose move 2"))
    }

    @Test
    fun touchRequiresASecondTapEvenForTheInitiallyFocusedMove() {
        val session = BattleSession()

        session.selectMoveWithTouch(0)

        assertFalse(session.chatMessages().last().contains("/choose move 1"))
        assertEquals(0, session.focusedMove)

        session.selectMoveWithTouch(0)

        assertTrue(session.chatMessages().last().contains("/choose move 1"))
    }

    @Test
    fun backCancelsGimmickAndTargetSelectionBeforeLeavingThePanel() {
        val session = BattleSession()
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        session.goBack()

        assertEquals(null, session.selectedGimmick)
        assertEquals(BattleSession.Panel.MOVES, session.panel)

        session.applyProtocolLine("|request|{\"active\":[{\"moves\":[{\"move\":\"Rock Slide\",\"pp\":10,\"target\":\"normal\"}]},{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"target\":\"self\"}]}]}")
        session.confirmSelection()
        assertTrue(session.targetOptions().isNotEmpty())
        session.goBack()

        assertTrue(session.targetOptions().isEmpty())
    }

    @Test
    fun modernXySpritesAreTheDefaultAndCanBeChanged() {
        val session = BattleSession()

        assertEquals(BattleSession.SpriteStyle.MODERN_3D, session.spriteStyle)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(8)
        session.confirmSelection()

        assertEquals(BattleSession.SpriteStyle.CLASSIC_2D, session.spriteStyle)
    }

    @Test
    fun requestExposesBattleGimmicksAndUsesTheSelectedGimmick() {
        val session = BattleSession()

        session.applyProtocolLine("|request|{\"active\":[{\"canZMove\":[{}],\"canMegaEvo\":true,\"canDynamax\":true,\"canTerastallize\":\"Fire\",\"moves\":[{\"move\":\"Flare Blitz\",\"type\":\"Fire\",\"pp\":15}]}]}")

        assertEquals(
            listOf(
                BattleSession.BattleGimmick.Z_POWER,
                BattleSession.BattleGimmick.MEGA_EVOLUTION,
                BattleSession.BattleGimmick.DYNAMAX,
                BattleSession.BattleGimmick.TERASTALLIZATION
            ),
            session.availableGimmicks()
        )
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        session.confirmSelection()

        assertTrue(session.chatMessages().last().contains("/choose move 1 zmove"))
        assertNull(session.selectedGimmick)
    }

    @Test
    fun detailsReflectKnownBattleInformation() {
        val session = BattleSession()

        session.applyProtocolLine("|-ability|p1a: Incineroar|Blaze")
        session.applyProtocolLine("|-item|p2a: Tapu Koko|Electrium Z")
        session.applyProtocolLine("|-damage|p1a: Incineroar|45/100 brn")

        assertEquals("Blaze", session.playerDetails().ability)
        assertEquals("Electrium Z", session.opponentDetails().item)
        assertEquals("45/100 brn", session.playerDetails().hp)
        assertEquals("BRN", session.playerDetails().condition)
    }

    @Test
    fun doublesKeepAbilityAndItemDetailsOnTheCorrectPartyMembers() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Naganadel|Naganadel, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|-ability|p1b: Naganadel|Beast Boost",
                "|-item|p1b: Naganadel|Dragonium Z",
                "|-endability|p1b: Naganadel"
            )
        )

        assertEquals("Suppressed", session.teamMemberDetails(1).ability)
        assertEquals("Dragonium Z", session.teamMemberDetails(1).item)
        assertEquals("Incineroar", session.playerDetails().name)
    }

    @Test
    fun battlePacketEmitsAClassifiedCriticalHitAndCarriesTheRequestId() {
        val session = BattleSession()
        val feedback = mutableListOf<BattleSession.BattleFeedback>()
        session.addFeedbackListener { feedback += it }

        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN",
                "|player|p2|GLADION",
                "|switch|p1a: Incineroar|Incineroar, L50, M|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|turn|1",
                "|request|{\"rqid\":27,\"active\":[{\"moves\":[{\"move\":\"Flare Blitz\",\"type\":\"Fire\",\"pp\":15}]}]}",
                "|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko",
                "|-damage|p2a: Tapu Koko|0 fnt",
                "|-supereffective|p2a: Tapu Koko",
                "|-crit|p2a: Tapu Koko",
                "|win|ADRIAN"
            )
        )

        assertEquals("0 fnt", session.opponentHp)
        assertEquals(0f, session.opponentHealthFraction())
        assertEquals("ADRIAN won the battle.", session.status)
        assertTrue(session.isBattleFinished())
        assertTrue(feedback.any { it.type == BattleSession.FeedbackType.POKEMON_CRY && it.actor == "Incineroar" })
        assertTrue(feedback.any { it.type == BattleSession.FeedbackType.MOVE && it.move == "Flare Blitz" })
        assertTrue(feedback.any { it.type == BattleSession.FeedbackType.HIT && it.impact == BattleSession.HitImpact.SUPER_EFFECTIVE_CRITICAL })

        session.applyProtocolLine("|request|{\"rqid\":28,\"active\":[{\"moves\":[{\"move\":\"Darkest Lariat\",\"type\":\"Dark\",\"pp\":10}]}]}")
        session.confirmSelection()

        assertTrue(session.chatMessages().last().contains("/choose move 1|28"))
    }

    @Test
    fun faintedCombatantIsRemovedFromTheBattlePresentation() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Incineroar|Incineroar, L50, M|316/316",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100"
            )
        )

        assertTrue(session.hasActivePlayerCombatant())
        assertTrue(session.hasActiveOpponentCombatant())

        session.applyProtocolPacket(
            listOf(
                "|-damage|p2a: Tapu Koko|0 fnt",
                "|faint|p2a: Tapu Koko"
            )
        )

        assertTrue(session.hasActivePlayerCombatant())
        assertTrue(!session.hasActiveOpponentCombatant())
    }

    @Test
    fun moveAnimationEventSurvivesDamageModifiers() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko",
                "|-damage|p2a: Tapu Koko|64/100",
                "|-supereffective|p2a: Tapu Koko"
            )
        )

        assertEquals("Incineroar used Flare Blitz!", session.latestMoveEvent)
        assertEquals("It's super effective!", session.latestBattleEvent)
    }

    @Test
    fun liveRequestsResolveMissingMoveTypesFromTheOfficialMoveDex() {
        val session = BattleSession()

        session.applyProtocolLine("|request|{\"active\":[{\"moves\":[{\"move\":\"Low Kick\",\"pp\":20},{\"move\":\"U-turn\",\"pp\":20},{\"move\":\"Knock Off\",\"pp\":20}]}]}")

        assertEquals(listOf("UNKNOWN", "UNKNOWN", "UNKNOWN"), session.moves().map { it.type })

        session.setMoveTypeResolver(mapOf("Low Kick" to "FIGHTING", "U-turn" to "BUG", "Knock Off" to "DARK")::get)

        assertEquals(listOf("FIGHTING", "BUG", "DARK"), session.moves().map { it.type })
    }

    @Test
    fun liveRequestsAndSwitchesResolveOfficialPokemonTypes() {
        val session = BattleSession()
        session.setPokemonTypeResolver(
            mapOf(
                "Mewtwo" to listOf("PSYCHIC"),
                "Magikarp" to listOf("WATER")
            )::get
        )

        session.applyProtocolLine(
            "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Mewtwo\",\"details\":\"Mewtwo, L50\",\"condition\":\"353/353\",\"active\":true}]}}"
        )
        session.applyProtocolLine("|switch|p2a: Magikarp|Magikarp, L1, F|11/11")

        assertEquals(listOf("PSYCHIC"), session.teamMemberDetails(0).types)
        assertEquals(listOf("WATER"), session.opponentDetails().types)
    }

    @Test
    fun battleInfoTracksFieldEffectsSideConditionsAndBoosts() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-weather|RainDance",
                "|-fieldstart|move: Electric Terrain",
                "|-sidestart|p1: ADRIAN|move: Stealth Rock",
                "|-sidestart|p2: GLADION|move: Reflect",
                "|-boost|p1a: Incineroar|atk|2",
                "|-unboost|p2a: Tapu Koko|spe|1"
            )
        )

        assertEquals("RainDance", session.battleInfo().weather)
        assertEquals("Electric Terrain", session.battleInfo().terrain)
        assertEquals(listOf("Stealth Rock"), session.battleInfo().playerSideConditions)
        assertEquals(listOf("Reflect"), session.battleInfo().opponentSideConditions)
        assertEquals(mapOf("atk" to 2), session.battleInfo().playerBoosts)
        assertEquals(mapOf("spe" to -1), session.battleInfo().opponentBoosts)

        session.applyProtocolPacket(
            listOf(
                "|-weather|none",
                "|-fieldend|move: Electric Terrain",
                "|-sideend|p1: ADRIAN|move: Stealth Rock",
                "|-clearallboost|"
            )
        )

        assertEquals("", session.battleInfo().weather)
        assertEquals("", session.battleInfo().terrain)
        assertTrue(session.battleInfo().playerSideConditions.isEmpty())
        assertTrue(session.battleInfo().playerBoosts.isEmpty())
        assertTrue(session.battleInfo().opponentBoosts.isEmpty())
    }

    @Test
    fun protocolStreamForwardsRenderablePacketsAndResetsItsBattleHistory() {
        val session = BattleSession()
        val received = mutableListOf<List<String>>()
        session.applyProtocolLine("|turn|1")
        session.addProtocolListener { received += it }

        session.applyProtocolPacket(listOf("ignored", "|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko"))

        assertEquals(
            listOf("|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko"),
            received.single()
        )
        assertEquals(
            listOf("|turn|1", "|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko"),
            session.protocolHistory()
        )

        session.applyProtocolLine("|init|battle")

        assertEquals(listOf("|init|battle"), session.protocolHistory())
    }

    @Test
    fun activityUnifiesBattleEventsAndChatMessages() {
        val session = BattleSession()

        session.applyProtocolLine("|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko")
        session.applyProtocolLine("|c|GLADION|Good luck!")

        assertEquals(
            listOf(
                "Battle started.",
                "Incineroar entered the field.",
                "Tapu Koko's Electric Surge activated!",
                "[Battle] Welcome to Showdown!",
                "[System] Controller and touch input are ready.",
                "Incineroar used Flare Blitz!",
                "[GLADION] Good luck!"
            ),
            session.activityMessages()
        )
    }

    @Test
    fun activitySuppressesRepeatedProtocolEvents() {
        val session = BattleSession()

        session.applyProtocolLine("|turn|2")
        session.applyProtocolLine("|turn|2")

        assertEquals(1, session.activityMessages().count { it == "Turn 2." })
    }

    @Test
    fun forceSwitchAndTeamPreviewEmitOfficialChoices() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener { decisions += it }

        session.applyProtocolLine("|request|{\"rqid\":31,\"forceSwitch\":[true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"0 fnt\",\"active\":false},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}")

        assertEquals(BattleSession.Panel.TEAM, session.panel)
        assertEquals(BattleSession.DecisionKind.SWITCH, session.decisionKind)
        session.moveFocus(1, 0)
        session.confirmSelection()

        assertEquals("/choose switch 2|31", decisions.last())

        session.applyProtocolLine("|request|{\"rqid\":32,\"teamPreview\":true,\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"100/100\"},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\"}]}}")

        assertEquals(BattleSession.DecisionKind.TEAM_PREVIEW, session.decisionKind)
        session.moveFocus(-1, 0)
        session.confirmSelection()
        session.moveFocus(1, 0)
        session.confirmSelection()

        assertEquals("/choose team 12|32", decisions.last())
    }

    @Test
    fun multiForceSwitchCollectsAllReplacementChoices() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"rqid\":33,\"forceSwitch\":[true,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"0 fnt\"},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\"},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"100/100\"}]}}"
        )

        session.moveFocus(1, 0)
        session.confirmSelection()
        session.moveFocus(1, 0)
        session.confirmSelection()

        assertEquals(listOf("/choose switch 2, switch 3|33"), decisions)
    }

    @Test
    fun liveRequestPublishesActualPartyHpAndConditions() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"rqid\":41,\"active\":[{\"moves\":[{\"move\":\"Fake Out\",\"type\":\"Normal\",\"pp\":10}]}],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"83/100 brn\",\"active\":true},{\"ident\":\"p1: Rotom-Wash\",\"details\":\"Rotom-Wash, L50\",\"condition\":\"0 fnt\",\"active\":false}]}}"
        )

        assertEquals("83/100 brn", session.teamMemberDetails(0).hp)
        assertEquals("BRN", session.teamMemberDetails(0).condition)
        assertEquals("83/100 brn", session.playerHp)
        assertEquals("BRN", session.playerCondition)
        assertEquals("0 fnt", session.teamMemberDetails(1).hp)
        assertEquals("FNT", session.teamMemberDetails(1).condition)
    }

    @Test
    fun livePartyDetailsPreserveBallMetadata() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"wait\":true,\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"0 fnt\",\"pokeball\":\"ultraball\"}]}}"
        )

        assertEquals("ultraball", session.playerPartyDetails().first().pokeball)
        assertEquals("FNT", session.playerPartyDetails().first().condition)
    }

    @Test
    fun unmatchedSwitchDoesNotOverwriteAnUnrelatedPartyMember() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"83/100\",\"active\":false},{\"ident\":\"p1: Rotom-Wash\",\"details\":\"Rotom-Wash, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )
        session.applyProtocolLine("|switch|p1a: Tornadus-Therian|Tornadus-Therian, L50|72/100")

        assertEquals("Tornadus-Therian", session.playerDetails().name)
        assertEquals("83/100", session.teamMemberDetails(0).hp)
        assertEquals("100/100", session.teamMemberDetails(1).hp)
    }

    @Test
    fun battleMenuEmitsLiveClientActions() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(0)
        session.confirmSelection()
        session.selectMenuItem(9)
        session.confirmSelection()
        session.selectMenuItem(10)
        session.confirmSelection()
        session.selectMenuItem(11)
        session.confirmSelection()
        session.selectMenuItem(12)
        session.confirmSelection()
        assertEquals(
            listOf(
                BattleSession.ClientAction.FIND_BATTLE,
                BattleSession.ClientAction.CONFIGURE_TEAM,
                BattleSession.ClientAction.OPEN_ROOMS,
                BattleSession.ClientAction.CONFIGURE_ACCOUNT,
                BattleSession.ClientAction.CONFIGURE_SERVER
            ),
            actions
        )
    }

    @Test
    fun battleMenuCanExportTheProtocolTranscript() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }
        session.applyProtocolLine("|init|battle")

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(13)
        session.confirmSelection()

        assertEquals(listOf(BattleSession.ClientAction.EXPORT_REPLAY), actions)
        assertTrue(session.protocolHistory().contains("|init|battle"))
    }

    @Test
    fun battleMenuUsesTheChallengeSlotWhenNoLiveBattleIsActive() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(3)
        session.confirmSelection()

        assertEquals(listOf(BattleSession.ClientAction.CHALLENGE_PLAYER), actions)
        assertEquals("Challenge player", session.menuItems()[3])

        session.setLocalUsername("ADRIAN")
        session.applyProtocolPacket(listOf("|player|p1|ADRIAN", "|player|p2|GLADION"))
        session.setLiveBattleActive(true)
        assertEquals("Forfeit", session.menuItems()[3])
    }

    @Test
    fun battleMenuControlsTheAuthoritativeBattleTimerState() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.applyProtocolPacket(listOf("|player|p1|ADRIAN", "|player|p2|GLADION", "|init|battle"))
        session.setLocalUsername("ADRIAN")
        session.setLiveBattleActive(true)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(14)
        session.confirmSelection()

        assertEquals(listOf(BattleSession.ClientAction.TOGGLE_BATTLE_TIMER), actions)
        assertEquals("Battle timer off", session.menuItems()[14])

        session.applyProtocolLine("|inactive|Time left: 60 sec this turn | 300 sec total | 30 sec grace")
        assertTrue(session.isBattleTimerEnabled())
        assertEquals("Battle timer on", session.menuItems()[14])

        session.applyProtocolLine("|inactiveoff|")
        assertFalse(session.isBattleTimerEnabled())
    }

    @Test
    fun completedBattleMenuOffersReplaySaving() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.applyProtocolLine("|win|ADRIAN")
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(14)
        session.confirmSelection()

        assertEquals("Save replay", session.menuItems()[14])
        assertEquals(listOf(BattleSession.ClientAction.SAVE_REPLAY), actions)
    }

    @Test
    fun spectatorBattleMenuOffersLeaveInsteadOfForfeit() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }
        session.setLocalUsername("ADRIAN")
        session.applyProtocolPacket(listOf("|player|p1|MISTY", "|player|p2|GLADION"))
        session.setLiveBattleActive(true)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(3)
        session.confirmSelection()

        assertEquals("Leave battle", session.menuItems()[3])
        assertEquals(listOf(BattleSession.ClientAction.LEAVE_BATTLE), actions)
    }

    @Test
    fun selectedMatchFormatDrivesSearchMenuState() {
        val session = BattleSession()

        session.setMatchFormat(BattleSession.MatchFormat.GEN9_RANDOM)

        assertEquals(BattleSession.MatchFormat.GEN9_RANDOM, session.matchFormat)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(0)
        session.confirmSelection()
        assertTrue(session.status.contains("[Gen 9] Random Battle"))
    }

    @Test
    fun userPreferencesRestoreIntoTheBattleSession() {
        val session = BattleSession()

        session.applyUserPreferences(
            touchConfirmation = false,
            soundEffects = false,
            music = true,
            haptics = false,
            spriteStyle = BattleSession.SpriteStyle.CLASSIC_2D
        )

        assertFalse(session.touchConfirmationEnabled)
        assertFalse(session.soundEffectsEnabled)
        assertTrue(session.musicEnabled)
        assertFalse(session.hapticsEnabled)
        assertEquals(BattleSession.SpriteStyle.CLASSIC_2D, session.spriteStyle)
    }

    @Test
    fun changingClientSettingsEmitsASettingsChangedAction() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(4)
        session.confirmSelection()

        assertEquals(listOf(BattleSession.ClientAction.SETTINGS_CHANGED), actions)
        assertFalse(session.soundEffectsEnabled)
    }

    @Test
    fun serverFormatCatalogDrivesTheAvailableBattleFormats() {
        val session = BattleSession()

        session.applyServerFormats(
            listOf(
                "|formats|,LL|,1|S/V Singles|[Gen 9] Random Battle,4f|[Gen 7] Random Battle,4f|,4|Past Gens Singles|[Gen 7] OU,e"
            )
        )

        assertEquals(
            listOf("gen9randombattle", "gen7randombattle", "gen7ou"),
            session.availableMatchFormats().map { it.id }
        )
        session.setMatchFormat(session.availableMatchFormats().last())
        assertEquals("[Gen 7] OU", session.matchFormat.label)
    }

    @Test
    fun serverFormatCatalogAcceptsIdFirstEntries() {
        val formats = BattleSession.parseServerFormats("|formats|gen9ou,[Gen 9] OU|gen9randombattle,[Gen 9] Random Battle,4f")

        assertEquals(listOf("gen9ou", "gen9randombattle"), formats.map { it.id })
        assertEquals("[Gen 9] Random Battle", formats[1].label)
        assertTrue(formats[1].usesRandomTeams)
    }

    @Test
    fun activityPanelRequestsChatComposer() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        val messages = mutableListOf<String>()
        session.addClientActionListener { actions += it }
        session.addChatListener { messages += it }

        session.selectPanel(BattleSession.Panel.ACTIVITY)
        session.confirmSelection()
        session.sendChat("gl hf")

        assertEquals(listOf(BattleSession.ClientAction.OPEN_CHAT), actions)
        assertEquals(listOf("gl hf"), messages)
        assertTrue(session.activityMessages().last().contains("gl hf"))
    }

    @Test
    fun replayModeKeepsTheBattleReadOnly() {
        val session = BattleSession()

        session.setLiveBattleActive(true)
        session.setReplayMode(true)

        assertTrue(session.isReplayMode())
        assertFalse(session.decisionAvailable)
        assertFalse(session.menuItems()[3].contains("Forfeit"))
    }

    @Test
    fun replayMenuOpensReplayControls() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.setReplayMode(true)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(13)
        session.confirmSelection()

        assertEquals("Replay controls", session.menuItems()[13])
        assertEquals(listOf(BattleSession.ClientAction.OPEN_REPLAY_CONTROLS), actions)
    }
}
