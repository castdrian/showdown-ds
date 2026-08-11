package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSessionTest {
    @Test
    fun newSessionsStartWithTheCurrentGen9RandomFormat() {
        assertEquals(BattleSession.MatchFormat.GEN9_RANDOM, BattleSession().matchFormat)
    }

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
    fun truncatedDamagePacketsDoNotBreakTheBattleSession() {
        val session = BattleSession()

        session.applyProtocolPacket(listOf("|-damage", "|-damage|"))

        assertTrue(session.battleLog().isNotEmpty())
    }

    @Test
    fun requestUsesDashesForMovesWithoutNumericAccuracy() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"accuracy\":\"always\"},{\"move\":\"Swift\",\"pp\":20,\"accuracy\":true},{\"move\":\"Thunderbolt\",\"pp\":15,\"accuracy\":85}]}]}"
        )

        assertEquals("—", session.moves()[0].accuracy)
        assertEquals("—", session.moves()[1].accuracy)
        assertEquals("85", session.moves()[2].accuracy)
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
    fun sentMoveCanBeCancelledUntilTheNextRequest() {
        val session = BattleSession()
        session.setLiveBattleActive(true)
        session.applyProtocolLine("|request|{\"rqid\":9,\"active\":[{\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15}]}]}")

        session.confirmSelection()

        assertTrue(session.canCancelChoice())

        session.applyProtocolLine("|request|{\"rqid\":10,\"active\":[{\"moves\":[{\"move\":\"Flamethrower\",\"pp\":14}]}]}")

        assertFalse(session.canCancelChoice())
    }

    @Test
    fun restoredBattleSideSurvivesARejoinWithoutTheOldGuestName() {
        val session = BattleSession()
        session.prepareForLobby()
        session.setLocalUsername("GuestNew")
        session.restoreBattlePlayerSlot("p2")
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|ThorOpp",
                "|player|p2|GuestOld"
            )
        )

        assertEquals("p2", session.battlePlayerSlot())
        assertEquals("GuestOld", session.playerName)
        assertEquals("ThorOpp", session.opponentName)
    }

    @Test
    fun endingLiveBattleClearsTheCancellableChoice() {
        val session = BattleSession()
        session.setLiveBattleActive(true)
        session.applyProtocolLine("|request|{\"rqid\":9,\"active\":[{\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15}]}]}")

        session.confirmSelection()
        assertTrue(session.canCancelChoice())

        session.setLiveBattleActive(false)

        assertFalse(session.canCancelChoice())
    }

    @Test
    fun deactivatingLiveBattleClearsTheBattlePrompt() {
        val session = BattleSession()
        session.setLiveBattleActive(true)
        session.applyProtocolLine("|request|{\"rqid\":9,\"active\":[{\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15}]}]}")

        session.setLiveBattleActive(false)

        assertFalse(session.isLiveBattleActive())
        assertFalse(session.decisionAvailable)
        assertEquals(BattleSession.DecisionKind.WAIT, session.decisionKind)
        assertEquals("Find a battle or challenge a player.", session.status)
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
        session.applyProtocolLine("|request|{\"rqid\":14,\"teamPreview\":true,\"chosenTeamSize\":6}")

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
        session.applyProtocolLine("|request|{\"rqid\":15,\"teamPreview\":true,\"chosenTeamSize\":6}")

        session.confirmSelection()
        session.goBack()

        assertTrue(session.decisionAvailable)
        assertTrue(session.teamPreviewOrder().isEmpty())
    }

    @Test
    fun teamPreviewSubmitsWhenTheRequestSelectionLimitIsReached() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine("|request|{\"rqid\":16,\"teamPreview\":true,\"maxChosenTeamSize\":2}")

        assertEquals(2, session.teamPreviewRequiredSize())
        session.confirmSelection()
        assertTrue(session.decisionAvailable)
        session.moveFocus(1, 0)
        session.confirmSelection()

        assertEquals(listOf("/choose team 12|16"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun protocolTeamPreviewSizeLimitsTheSelectionWhenRequestOmitsIt() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener(decisions::add)
        session.applyProtocolPacket(
            listOf(
                "|teampreview|2",
                "|request|{\"rqid\":17,\"teamPreview\":true}"
            )
        )

        assertEquals(2, session.teamPreviewRequiredSize())
        session.confirmSelection()
        session.moveFocus(1, 0)
        session.confirmSelection()

        assertEquals(listOf("/choose team 12|17"), decisions)
    }

    @Test
    fun protocolLinesUpdateBattleState() {
        val session = BattleSession()

        session.applyProtocolLine("|player|p1|ADRIAN")
        session.applyProtocolLine("|player|p2|GLADION")
        session.applyProtocolLine("|gametype|doubles")
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
        assertEquals("doubles", session.gameType)
        assertFalse(session.isSinglesBattle())
        assertTrue(session.battleLog().last().contains("Flare Blitz"))
    }

    @Test
    fun multiBattlePartnersShareTheLocalSide() {
        val session = BattleSession()
        session.setLocalUsername("ALLY")
        session.applyProtocolPacket(
            listOf(
                "|player|p1|PARTNER",
                "|player|p2|FOE",
                "|player|p3|ALLY",
                "|player|p4|FOE2",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p3a: Mimikyu|Mimikyu, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p4a: Landorus|Landorus, L50|100/100"
            )
        )

        assertEquals("ALLY", session.playerName)
        assertEquals("FOE", session.opponentName)
        assertEquals(listOf("Incineroar", "Mimikyu"), session.playerActiveCombatants().map { it.name })
        assertEquals(listOf("Tapu Koko", "Landorus"), session.opponentActiveCombatants().map { it.name })
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
    fun touchSelectsAndSendsAMoveOnTheFirstTap() {
        val session = BattleSession()

        session.selectMoveWithTouch(2)
        assertTrue(session.chatMessages().last().contains("/choose move 3"))
        assertEquals("Incineroar chose Darkest Lariat.", session.latestBattleEvent)
        assertEquals("Move sent: Darkest Lariat", session.status)
    }

    @Test
    fun touchSelectsTheInitiallyFocusedMoveOnTheFirstTap() {
        val session = BattleSession()

        session.selectMoveWithTouch(0)

        assertTrue(session.chatMessages().last().contains("/choose move 1"))
        assertEquals(0, session.focusedMove)
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
        session.selectMenuItem(7)
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
        assertEquals("Fire", session.terastallizeType())
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        session.confirmSelection()

        assertTrue(session.chatMessages().last().contains("/choose move 1 zmove"))
        assertNull(session.selectedGimmick)
    }

    @Test
    fun falseTerastallizeCapabilityDoesNotCreateATypeOrGimmick() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"active\":[{\"canTerastallize\":false,\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
        )

        assertEquals("", session.terastallizeType())
        assertFalse(session.availableGimmicks().contains(BattleSession.BattleGimmick.TERASTALLIZATION))
    }

    @Test
    fun gimmickChoicesUseOfficialDynamaxAndTerastalizeSuffixes() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"canTerastallize\":\"Water\",\"moves\":[{\"move\":\"Surf\",\"pp\":15}]}]}"
        )

        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)
        session.confirmSelection()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canTerastallize\":\"Water\",\"moves\":[{\"move\":\"Surf\",\"pp\":14}]}]}"
        )
        session.selectGimmick(BattleSession.BattleGimmick.TERASTALLIZATION)
        session.confirmSelection()

        assertEquals(
            listOf("/choose move 1 max", "/choose move 1 terastalize"),
            decisions
        )
    }

    @Test
    fun noCancelRequestsDoNotExposeCancellationAfterSubmittingAChoice() {
        val session = BattleSession()
        session.setLiveBattleActive(true)
        session.applyProtocolLine("|request|{\"rqid\":18,\"noCancel\":true,\"active\":[{\"moves\":[{\"move\":\"Flare Blitz\",\"pp\":15}]}]}")

        session.confirmSelection()

        assertFalse(session.canCancelChoice())
    }

    @Test
    fun zAndMaxMoveRequestsExposeTheirAlternateMoveNamesWhenSelected() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"zMoves\":[{\"move\":\"Inferno Overdrive\",\"type\":\"Fire\",\"target\":\"normal\"}],\"maxMoves\":[{\"move\":\"Max Flare\",\"type\":\"Fire\",\"target\":\"normal\"}],\"moves\":[{\"move\":\"Flamethrower\",\"type\":\"Fire\",\"pp\":15,\"target\":\"normal\"}]}]}"
        )

        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        assertEquals("Inferno Overdrive", session.moves().single().name)
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)

        assertEquals("Max Flare", session.moves().single().name)
    }

    @Test
    fun lateMoveDexResolutionUpdatesGimmickMoveDetails() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"zMoves\":[{\"move\":\"Inferno Overdrive\",\"target\":\"normal\"}],\"maxMoves\":[{\"move\":\"Max Flare\",\"target\":\"normal\"}],\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15,\"target\":\"normal\"}]}]}"
        )

        session.setMoveInfoResolver(
            mapOf(
                "Flamethrower" to BattleSession.MoveInfo("90", "100", "Special")
            )::get
        )
        session.setMoveTypeResolver(
            mapOf(
                "Flamethrower" to "FIRE"
            )::get
        )

        assertEquals("90", session.moves().single().power)
        assertEquals("100", session.moves().single().accuracy)
        assertEquals("Special", session.moves().single().category)
        assertEquals("FIRE", session.moves().single().type)
        assertTrue(session.availableGimmicks().contains(BattleSession.BattleGimmick.Z_POWER))
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        assertEquals("Inferno Overdrive", session.moves().single().name)
        assertEquals("175", session.moves().single().power)
        assertEquals("—", session.moves().single().accuracy)
        assertEquals("Special", session.moves().single().category)
        assertEquals("FIRE", session.moves().single().type)
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)
        assertEquals("130", session.moves().single().power)
        assertEquals("—", session.moves().single().accuracy)
    }

    @Test
    fun repeatedMoveDexResolutionRefreshesDerivedMoveDetails() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"zMoves\":[{\"move\":\"Inferno Overdrive\",\"target\":\"normal\"}],\"maxMoves\":[{\"move\":\"Max Flare\",\"target\":\"normal\"}],\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15,\"target\":\"normal\"}]}]}"
        )
        session.setMoveInfoResolver { BattleSession.MoveInfo("90", "100", "Special") }
        session.setMoveTypeResolver { "FIRE" }
        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)
        assertEquals("130", session.moves().single().power)

        session.setMoveInfoResolver { BattleSession.MoveInfo("120", "80", "Special") }

        assertEquals("140", session.moves().single().power)
        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        assertEquals("190", session.moves().single().power)
    }

    @Test
    fun maxMovePowerUsesVariantTypeWhenBaseTypeIsNotInRequest() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"maxMoves\":[{\"move\":\"Max Knuckle\",\"type\":\"Fighting\",\"target\":\"normal\"}],\"moves\":[{\"move\":\"Focus Blast\",\"pp\":10,\"target\":\"normal\"}]}]}"
        )
        session.setMoveInfoResolver { name ->
            if (name == "Focus Blast") BattleSession.MoveInfo("120", "70", "Special") else null
        }

        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)

        assertEquals("95", session.moves().single().power)
        assertEquals("—", session.moves().single().accuracy)
    }

    @Test
    fun fixedGimmickMovePowerUsesSpecialMoveDexValue() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"zMoves\":[{\"move\":\"Catastropika\",\"target\":\"normal\"}],\"maxMoves\":[{\"move\":\"G-Max Drum Solo\",\"target\":\"normal\"}],\"moves\":[{\"move\":\"Thunderbolt\",\"pp\":15,\"target\":\"normal\"}]}]}"
        )
        val moveInfo = mapOf(
            "Thunderbolt" to BattleSession.MoveInfo("90", "100", "Special"),
            "Catastropika" to BattleSession.MoveInfo("210", "—", "Physical", true),
            "G-Max Drum Solo" to BattleSession.MoveInfo("160", "—", "Physical", true)
        )
        session.setMoveInfoResolver(moveInfo::get)

        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        assertEquals("210", session.moves().single().power)
        session.selectGimmick(BattleSession.BattleGimmick.Z_POWER)
        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)
        assertEquals("160", session.moves().single().power)
    }

    @Test
    fun explicitNonNumericGimmickMetricsRemainDashesAfterDexResolution() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"maxMoves\":[{\"move\":\"Max Flare\",\"basePower\":0,\"accuracy\":true,\"target\":\"normal\"}],\"moves\":[{\"move\":\"Flamethrower\",\"pp\":15,\"target\":\"normal\"}]}]}"
        )
        session.setMoveInfoResolver { BattleSession.MoveInfo("90", "100", "Special") }
        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)

        assertEquals("—", session.moves().single().power)
        assertEquals("—", session.moves().single().accuracy)
    }

    @Test
    fun lateMoveDexResolutionPreservesExplicitNonNumericMoveValues() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"basePower\":0,\"accuracy\":true}]}]}"
        )

        session.setMoveInfoResolver { BattleSession.MoveInfo("90", "100") }

        assertEquals("—", session.moves().single().power)
        assertEquals("—", session.moves().single().accuracy)
    }

    @Test
    fun requestExposesMegaVariantsAndUltraBurstWithOfficialChoiceSuffixes() {
        val session = BattleSession()

        session.applyProtocolLine("|request|{\"active\":[{\"canMegaEvoX\":true,\"canMegaEvoY\":true,\"canUltraBurst\":true,\"moves\":[{\"move\":\"Flare Blitz\",\"type\":\"Fire\",\"pp\":15}]}]}")

        assertEquals(
            listOf(
                BattleSession.BattleGimmick.MEGA_EVOLUTION_X,
                BattleSession.BattleGimmick.MEGA_EVOLUTION_Y,
                BattleSession.BattleGimmick.ULTRA_BURST
            ),
            session.availableGimmicks()
        )

        session.selectGimmick(BattleSession.BattleGimmick.MEGA_EVOLUTION_X)
        session.confirmSelection()

        assertTrue(session.chatMessages().last().contains("/choose move 1 megax"))
    }

    @Test
    fun requestAcceptsStringMegaCapabilityFromCustomFormats() {
        val session = BattleSession()

        session.applyProtocolLine("|request|{\"active\":[{\"canMegaEvo\":\"Incineroar-Mega\",\"moves\":[{\"move\":\"Flare Blitz\",\"pp\":15}]}]}")

        assertEquals(listOf(BattleSession.BattleGimmick.MEGA_EVOLUTION), session.availableGimmicks())
        session.selectGimmick(BattleSession.BattleGimmick.MEGA_EVOLUTION)
        session.confirmSelection()

        assertTrue(session.chatMessages().last().contains("/choose move 1 mega"))
    }

    @Test
    fun multiActiveRequestsAllowOnlyOneGimmickFamilyPerTurn() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"rqid\":20,\"active\":[{\"canMegaEvo\":true,\"canMegaEvoX\":true,\"canDynamax\":true,\"moves\":[{\"move\":\"Protect\",\"pp\":10}]},{\"canMegaEvoY\":true,\"canDynamax\":true,\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}]}"
        )

        session.selectGimmick(BattleSession.BattleGimmick.MEGA_EVOLUTION)
        session.confirmSelection()

        assertFalse(session.availableGimmicks().contains(BattleSession.BattleGimmick.MEGA_EVOLUTION_X))
        assertTrue(session.availableGimmicks().contains(BattleSession.BattleGimmick.DYNAMAX))
        assertFalse(session.availableGimmicks().contains(BattleSession.BattleGimmick.MEGA_EVOLUTION_Y))
    }

    @Test
    fun requestTypeAndBattleFormatChooseOfficialTeamPreviewDefaults() {
        val session = BattleSession()
        session.applyProtocolLine("|gametype|doubles")
        session.applyProtocolLine("|request|{\"requestType\":\"team\",\"side\":{\"pokemon\":[]}}")

        assertEquals(BattleSession.DecisionKind.TEAM_PREVIEW, session.decisionKind)
        assertEquals(2, session.teamPreviewRequiredSize())

        session.applyProtocolLine("|request|{\"requestType\":\"wait\"}")

        assertEquals(BattleSession.DecisionKind.WAIT, session.decisionKind)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun illusionTeamPreviewRequestsTheFullTeamOrder() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"requestType\":\"team\",\"side\":{\"pokemon\":[{\"details\":\"Zoroark, L50\",\"baseAbility\":\"Illusion\"},{\"details\":\"Incineroar, L50\"},{\"details\":\"Naganadel, L50\"},{\"details\":\"Mimikyu, L50\"},{\"details\":\"Landorus, L50\"},{\"details\":\"Ferrothorn, L50\"}]}}"
        )

        assertEquals(6, session.teamPreviewRequiredSize())
    }

    @Test
    fun gigantamaxRequestsUseTheOfficialGimmickLabel() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"gigantamax\":true,\"moves\":[{\"move\":\"Max Flare\",\"pp\":5}]}]}"
        )

        assertEquals("Gigantamax", session.gimmickLabel(BattleSession.BattleGimmick.DYNAMAX))
        session.selectGimmick(BattleSession.BattleGimmick.DYNAMAX)
        assertTrue(session.status.contains("Gigantamax"))
    }

    @Test
    fun anyTargetMovesExposeOpponentsAndAlliesInMultiBattles() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|gametype|doubles",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Naganadel|Naganadel, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p2b: Druddigon|Druddigon, L50|100/100",
                "|request|{\"targetable\":true,\"active\":[{\"moves\":[{\"move\":\"Trick\",\"pp\":10,\"target\":\"any\"}]},{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
            )
        )

        assertEquals(listOf("1", "2", "-2"), session.targetOptions().map { it.choice })
    }

    @Test
    fun triplesRespectAdjacentTargetRulesAndAutomaticTargetMoves() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|gametype|triples",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Naganadel|Naganadel, L50|100/100",
                "|switch|p1c: Mimikyu|Mimikyu, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p2b: Druddigon|Druddigon, L50|100/100",
                "|switch|p2c: Landorus|Landorus, L50|100/100",
                "|request|{\"targetable\":true,\"active\":[{\"moves\":[{\"move\":\"Helping Hand\",\"pp\":10,\"target\":\"adjacentAlly\"}]},null,null]}"
            )
        )

        assertEquals(listOf("-2"), session.targetOptions().map { it.choice })

        session.applyProtocolLine(
            "|request|{\"targetable\":true,\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"target\":\"normal\"}]},null,null]}"
        )

        assertEquals(listOf("2", "3"), session.targetOptions().map { it.choice })

        session.applyProtocolLine(
            "|request|{\"targetable\":true,\"active\":[null,{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"target\":\"normal\"}]},null]}"
        )

        assertEquals(listOf("1", "2", "3"), session.targetOptions().map { it.choice })

        session.applyProtocolLine(
            "|request|{\"targetable\":true,\"active\":[null,null,{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"target\":\"adjacentFoe\"}]}]}"
        )

        assertEquals(listOf("1", "2"), session.targetOptions().map { it.choice })

        session.applyProtocolLine(
            "|request|{\"targetable\":true,\"active\":[{\"moves\":[{\"move\":\"Rock Slide\",\"pp\":10,\"target\":\"allAdjacentFoes\"}]},null,null]}"
        )

        assertTrue(session.targetOptions().isEmpty())
    }

    @Test
    fun commandingActiveSlotsArePassedAutomatically() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":34,\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"100/100\",\"active\":true,\"commanding\":true}]}}"
        )

        assertEquals(listOf("/choose pass|34"), decisions)
        assertFalse(session.decisionAvailable)
        assertEquals("Choice sent. Waiting for the other player…", session.status)
    }

    @Test
    fun revivalSwitchRequestsAllowARevivingFaintedPokemon() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":35,\"forceSwitch\":[true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"100/100\",\"active\":true,\"reviving\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"0 fnt\",\"active\":false}]}}"
        )

        session.moveFocus(1, 0)
        session.confirmSelection()

        assertEquals(listOf("/choose switch 2|35"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun triplesExposeAndSubmitTheOfficialShiftChoice() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolPacket(
            listOf(
                "|gametype|triples",
                "|request|{\"rqid\":36,\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]},null,null]}"
            )
        )

        assertTrue(session.canShift())
        session.selectShiftWithTouch()

        assertEquals(listOf("/choose shift, pass, pass|36"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun triplesOnlyAllowShiftFromTheEdgeSlots() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolPacket(
            listOf(
                "|gametype|triples",
                "|request|{\"rqid\":37,\"active\":[null,{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]},null]}"
            )
        )

        assertFalse(session.canShift())

        session.applyProtocolPacket(
            listOf(
                "|request|{\"rqid\":38,\"active\":[null,null,{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
            )
        )

        assertTrue(session.canShift())
        session.selectShiftWithTouch()
        assertEquals(listOf("/choose pass, pass, shift|38"), decisions)
    }

    @Test
    fun pokemonPanelCanSubmitARequestedVoluntarySwitch() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|request|{\"rqid\":21,\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
            )
        )

        session.selectPanel(BattleSession.Panel.TEAM)
        session.moveFocus(1, 0)
        session.confirmSelection()

        assertEquals(listOf("/choose switch 2|21"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun trappedActivePokemonCannotSubmitAVoluntarySwitch() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":22,\"active\":[{\"trapped\":true,\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
        )

        session.selectPanel(BattleSession.Panel.TEAM)
        session.moveFocus(1, 0)
        session.confirmSelection()

        assertTrue(decisions.isEmpty())
        assertTrue(session.status.contains("trapped"))
        assertTrue(session.decisionAvailable)
    }

    @Test
    fun explicitlyUntargetableRequestsDoNotOpenTargetSelection() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"targetable\":false,\"active\":[{\"moves\":[{\"move\":\"Rock Slide\",\"pp\":10,\"target\":\"normal\"}]},{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"target\":\"self\"}]}]}"
        )

        session.focusMove(0)

        assertTrue(session.targetOptions().isEmpty())
    }

    @Test
    fun uncertainMoveAndSwitchRequestsDisableUndoOnTheFinalChoice() {
        val moveSession = BattleSession()
        moveSession.setLiveBattleActive(true)
        moveSession.applyProtocolLine(
            "|request|{\"targetable\":false,\"active\":[{\"maybeDisabled\":true,\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
        )
        moveSession.confirmSelection()

        assertFalse(moveSession.canCancelChoice())

        val switchSession = BattleSession()
        switchSession.setLiveBattleActive(true)
        switchSession.applyProtocolLine(
            "|request|{\"active\":[{\"maybeTrapped\":true,\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
        )
        switchSession.selectPanel(BattleSession.Panel.TEAM)
        switchSession.moveFocus(1, 0)
        switchSession.confirmSelection()

        assertFalse(switchSession.canCancelChoice())
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
    fun consumingAnItemRemovesItFromBattleDetailsAndLogsTheEvent() {
        val session = BattleSession()
        session.applyProtocolLine("|-item|p1a: Incineroar|Sitrus Berry")

        session.applyProtocolLine("|-eat|p1a: Incineroar|Sitrus Berry")

        assertEquals("No item", session.playerDetails().item)
        assertTrue(session.battleLog().any { it.contains("consumed Sitrus Berry") })
    }

    @Test
    fun taggedEndItemPacketsRepresentConsumedItems() {
        val session = BattleSession()
        session.applyProtocolLine("|-item|p1a: Incineroar|Sitrus Berry")

        session.applyProtocolLine("|-enditem|p1a: Incineroar|Sitrus Berry|[eat]")

        assertEquals("No item", session.playerDetails().item)
        assertTrue(session.battleLog().any { it.contains("consumed Sitrus Berry") })
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
    fun doublesKeepOpponentSideDetailsOutOfThePrimaryCombatantCard() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p2b: Naganadel|Naganadel, L50|100/100",
                "|-item|p2b: Naganadel|Dragonium Z",
                "|-ability|p2b: Naganadel|Beast Boost"
            )
        )

        assertEquals("Unknown item", session.opponentDetails().item)
        assertEquals("Unknown ability", session.opponentDetails().ability)
        val sideMember = session.opponentPartyDetails().first { it.name == "Naganadel" }
        assertEquals("Dragonium Z", sideMember.item)
        assertEquals("Beast Boost", sideMember.ability)
    }

    @Test
    fun nicknameIdentifiersUpdateTheActiveSpeciesDetails() {
        val session = BattleSession()
        session.applyProtocolLine("|switch|p1a: Sparky|Pikachu, L50|100/100")

        session.applyProtocolLine("|-item|p1a: Sparky|Light Ball")
        session.applyProtocolLine("|-ability|p1a: Sparky|Static")

        assertEquals("Pikachu", session.playerDetails().name)
        assertEquals("Light Ball", session.playerDetails().item)
        assertEquals("Static", session.playerDetails().ability)
    }

    @Test
    fun duplicateSpeciesKeepNicknameSpecificPartyDetailsSeparated() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Sparky\",\"details\":\"Pikachu, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p1: Bolt\",\"details\":\"Pikachu, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )
        session.applyProtocolLine("|switch|p1a: Sparky|Pikachu, L50|100/100")
        session.applyProtocolLine("|-item|p1a: Sparky|Light Ball")
        session.applyProtocolLine("|switch|p1a: Bolt|Pikachu, L50|100/100")
        session.applyProtocolLine("|-item|p1a: Bolt|Magnet")

        assertEquals("Light Ball", session.teamMemberDetails(0).item)
        assertEquals("Magnet", session.teamMemberDetails(1).item)
    }

    @Test
    fun duplicateOpponentSpeciesKeepNicknameSpecificPartyDetailsSeparated() {
        val session = BattleSession()
        val packed = ShowdownTeamCodec.pack(
            listOf(
                ShowdownTeamSet(nickname = "Sparky", species = "Pikachu", item = "Light Ball", ability = "Static"),
                ShowdownTeamSet(nickname = "Bolt", species = "Pikachu", item = "Magnet", ability = "Lightning Rod")
            )
        )
        session.applyProtocolLine("|showteam|p2|$packed")
        session.applyProtocolLine("|switch|p2a: Sparky|Pikachu, L50|100/100")
        session.applyProtocolLine("|-item|p2a: Sparky|Light Ball")
        session.applyProtocolLine("|switch|p2a: Bolt|Pikachu, L50|100/100")
        session.applyProtocolLine("|-item|p2a: Bolt|Magnet")

        assertEquals("Light Ball", session.opponentPartyDetails()[0].item)
        assertEquals("Magnet", session.opponentPartyDetails()[1].item)
    }

    @Test
    fun longFormNamesUseTheBaseSpeciesInReadableBattleLabels() {
        assertEquals("Alcremie", BattleSession.displayPokemonName("Alcremie-Caramel-Swirl"))
        assertEquals("Alcremie", BattleSession.displayPokemonName("Alcremie-Caramel-Swirl", "Alcremie"))
        assertEquals("Rotom-Wash", BattleSession.displayPokemonName("Rotom-Wash"))
        assertEquals("Creamy", BattleSession.displayPokemonName("Creamy", "Alcremie-Caramel-Swirl"))
        assertEquals(
            "Alcremie-Caramel-Swirl",
            BattleSession.displayPokemonName("Alcremie-Caramel-Swirl", "Alcremie-Mint-Cream")
        )
    }

    @Test
    fun longFormSwitchKeepsTheFullFormInBattleState() {
        val session = BattleSession()

        session.applyProtocolLine("|switch|p1a: Alcremie-Caramel-Swirl|Alcremie-Caramel-Swirl, L50|100/100")

        assertEquals("Alcremie-Caramel-Swirl", session.playerPokemon)
        assertEquals("Alcremie-Caramel-Swirl", session.playerDetails().species)
        assertEquals("Alcremie", BattleSession.displayPokemonName(session.playerPokemon, session.playerDetails().species))
    }

    @Test
    fun longFormNamesStayShortInUserFacingBattleMessages() {
        val session = BattleSession()

        session.applyProtocolLine("|switch|p1a: Alcremie-Caramel-Swirl|Alcremie-Caramel-Swirl, L50|100/100")
        session.selectMoveWithTouch(0)

        assertEquals("Alcremie chose Fake Out.", session.latestBattleEvent)
        assertEquals("Alcremie chose Fake Out.", session.battleLog().last())
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
    fun hitModifiersBeforeDamageRemainAttachedToTheMoveImpact() {
        val session = BattleSession()
        val feedback = mutableListOf<BattleSession.BattleFeedback>()
        session.addFeedbackListener { feedback += it }

        session.applyProtocolPacket(
            listOf(
                "|move|p1a: Incineroar|Flare Blitz|p2a: Tapu Koko",
                "|-supereffective|p2a: Tapu Koko",
                "|-crit|p2a: Tapu Koko",
                "|-damage|p2a: Tapu Koko|10/100"
            )
        )

        assertTrue(feedback.any { it.type == BattleSession.FeedbackType.HIT && it.impact == BattleSession.HitImpact.SUPER_EFFECTIVE_CRITICAL })
        assertTrue(session.battleLog().contains("A critical hit!"))
        assertTrue(session.battleLog().contains("It's super effective!"))
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

        session.applyProtocolLine("|request|{\"rqid\":32,\"teamPreview\":true,\"chosenTeamSize\":2,\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"100/100\"},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\"}]}}")

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
    fun forcedSwitchesAutoPassWhenNoReplacementIsAvailable() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":39,\"forceSwitch\":[true,false],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )

        assertEquals(listOf("/choose pass|39"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun forcedSwitchesPadMissingReplacementsWithPasses() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":40,\"forceSwitch\":[true,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )

        session.moveFocus(2, 0)
        session.confirmSelection()

        assertEquals(listOf("/choose switch 3, pass|40"), decisions)
        assertFalse(session.decisionAvailable)
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
    fun inactiveHealthAndStatusPacketsUpdateOnlyTheirPartyEntry() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"rqid\":42,\"active\":[{\"moves\":[{\"move\":\"Fake Out\",\"type\":\"Normal\",\"pp\":10}]}],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50, M\",\"condition\":\"83/100\",\"active\":true},{\"ident\":\"p1: Rotom-Wash\",\"details\":\"Rotom-Wash, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )
        session.applyProtocolPacket(
            listOf(
                "|-heal|p1: Rotom-Wash|90/100",
                "|-status|p1: Rotom-Wash|par"
            )
        )

        assertEquals("83/100", session.playerHp)
        assertEquals("READY", session.playerCondition)
        assertEquals("90/100", session.teamMemberDetails(1).hp)
        assertEquals("PAR", session.teamMemberDetails(1).condition)
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
        session.selectMenuItem(8)
        session.confirmSelection()
        session.selectMenuItem(9)
        session.confirmSelection()
        session.selectMenuItem(10)
        session.confirmSelection()
        session.selectMenuItem(11)
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
    fun battleMenuOpensBattleControls() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }
        session.applyProtocolLine("|init|battle")

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(12)
        session.confirmSelection()

        assertEquals(listOf(BattleSession.ClientAction.OPEN_REPLAY_CONTROLS), actions)
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
        session.selectMenuItem(13)
        session.confirmSelection()

        assertEquals(listOf(BattleSession.ClientAction.TOGGLE_BATTLE_TIMER), actions)
        assertEquals("Battle timer off", session.menuItems()[13])

        session.applyProtocolLine("|inactive|Time left: 60 sec this turn | 300 sec total | 30 sec grace")
        assertTrue(session.isBattleTimerEnabled())
        assertEquals("Battle timer on", session.menuItems()[13])

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
        session.selectMenuItem(13)
        session.confirmSelection()

        assertEquals("Save replay", session.menuItems()[13])
        assertEquals(listOf(BattleSession.ClientAction.SAVE_REPLAY), actions)
    }

    @Test
    fun matchmakingActionCanReplaceItsInitialStatus() {
        val session = BattleSession()
        session.addClientActionListener { action ->
            if (action == BattleSession.ClientAction.FIND_BATTLE) session.setConnectionStatus("Battle search cancelled.")
        }

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(0)
        session.confirmSelection()

        assertEquals("Battle search cancelled.", session.status)
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
            soundEffects = false,
            music = true,
            haptics = false,
            spriteStyle = BattleSession.SpriteStyle.CLASSIC_2D
        )

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
        session.selectMenuItem(12)
        session.confirmSelection()

        assertEquals("Replay controls", session.menuItems()[12])
        assertEquals(listOf(BattleSession.ClientAction.OPEN_REPLAY_CONTROLS), actions)
    }
}
