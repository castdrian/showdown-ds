package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSessionTest {
    @Test
    fun newSessionsStartWithTheCurrentGen9RandomFormat() {
        assertEquals(BattleSession.MatchFormat.GEN9_RANDOM, BattleSession().matchFormat)
    }

    @Test
    fun waitingStatusDoesNotExposeTheLegacyHdMatchupLabel() {
        val session = BattleSession()

        session.setConnectionStatus("HD matchup challenge sent to Gladion.")

        assertEquals("[Gen 9] Random Battle challenge sent to Gladion.", session.status)
    }

    @Test
    fun formatControlsDoNotExposeTheLegacyHdMatchupLabel() {
        val session = BattleSession()
        session.setMatchFormat(BattleSession.MatchFormat("gen9randombattle", "HD matchup", "HD matchup"))

        assertEquals("Battle format: [Gen 9] Random Battle", session.status)
        assertEquals("Find a [Gen 9] Random Battle", session.menuItems()[0])
        assertEquals("Battle format Gen 9 Random", session.menuItems()[1])

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(0)
        session.confirmSelection()

        assertEquals("Connecting to a [Gen 9] Random Battle…", session.status)
    }

    @Test
    fun matchmakingMenuTurnsIntoAVisibleCancelActionWhileSearching() {
        val session = BattleSession()

        session.setBattleSearchActive(true)

        assertEquals("Cancel battle search", session.menuItems()[0])
        assertTrue(session.isBattleSearchActive())

        session.setBattleSearchActive(false)

        assertEquals("Find a [Gen 9] Random Battle", session.menuItems()[0])
    }

    @Test
    fun upperBattleFeedOmitsTurnMarkersWithoutChangingTheFullTranscript() {
        val session = BattleSession()
        session.appendShowdownBattleLog("Battle started!<br />Turn 1<br />Go! Pikachu!")

        assertEquals(listOf("Battle started!", "Go! Pikachu!"), session.battleFeedEntries())
        assertEquals(listOf("Battle started!", "Turn 1", "Go! Pikachu!"), session.showdownBattleLog())
    }

    @Test
    fun upperBattleFeedReusesUnchangedEntriesAndInvalidatesAfterNewEvents() {
        val session = BattleSession()

        val first = session.battleFeedEntries()
        val latest = session.battleFeedEntries(1)

        assertSame(first, session.battleFeedEntries())
        assertSame(latest, session.battleFeedEntries(1))

        session.appendShowdownBattleLog("Pikachu used Thunderbolt!")
        val second = session.battleFeedEntries()

        assertEquals("Pikachu used Thunderbolt!", second.last())
        assertSame(second, session.battleFeedEntries())
    }

    @Test
    fun upperBattleFeedOmitsTimerAndRatingMetadataWhileActivityKeepsIt() {
        val session = BattleSession()
        session.appendShowdownBattleLog(
            "Pikachu used Thunderbolt!<br />Guest 26464262 has 15 seconds left.<br />ADRIAN's rating: 1053 → 1080"
        )

        assertEquals(listOf("Pikachu used Thunderbolt!"), session.battleFeedEntries())
        assertEquals(
            listOf(
                "Pikachu used Thunderbolt!",
                "Guest 26464262 has 15 seconds left.",
                "ADRIAN's rating: 1053 → 1080"
            ),
            session.activityMessages().takeLast(3)
        )
    }

    @Test
    fun upperBattleFeedOmitsBattleTimerAnnouncementsWhileActivityKeepsThem() {
        val session = BattleSession()
        session.appendShowdownBattleLog(
            "Battle timer is ON: inactive players will automatically lose when time's up. (requested by Guest)<br />Pikachu used Thunderbolt!<br />The battle timer is off."
        )

        assertEquals(listOf("Pikachu used Thunderbolt!"), session.battleFeedEntries())
        assertEquals(
            listOf(
                "Battle timer is ON: inactive players will automatically lose when time's up. (requested by Guest)",
                "Pikachu used Thunderbolt!",
                "The battle timer is off."
            ),
            session.activityMessages().takeLast(3)
        )
    }

    @Test
    fun upperBattleFeedOmitsBattleMetadataWhileActivityKeepsIt() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|gametype|singles",
                "|gen|9",
                "|tier|[Gen 9] Random Battle",
                "|teamsize|p1|6",
                "|rule|Species Clause: Limit one of each Pokémon",
                "|rated"
            )
        )

        assertEquals(listOf("Battle started."), session.battleFeedEntries())
        assertTrue(session.battleLog().contains("Battle type: Singles."))
        assertTrue(session.battleLog().contains("Generation 9 battle."))
        assertTrue(session.battleLog().contains("Format: [Gen 9] Random Battle"))
        assertTrue(session.battleLog().contains("p1 team size: 6"))
        assertTrue(session.battleLog().contains("Rule: Species Clause: Limit one of each Pokémon"))
        assertTrue(session.battleLog().contains("Rated battle."))
    }

    @Test
    fun battleTierDoesNotExposeTheLegacyHdMatchupLabel() {
        val session = BattleSession()

        session.applyProtocolPacket(listOf("|init|battle", "|tier|HD matchup"))

        assertEquals("[Gen 9] Random Battle", session.format)
        assertTrue(session.battleLog().contains("Format: [Gen 9] Random Battle"))
        assertFalse(session.battleLog().any { it.contains("HD matchup", true) })
    }

    @Test
    fun upperBattleFeedKeepsMoreThanThePreviousFiveVisibleEntries() {
        val session = BattleSession()
        session.appendShowdownBattleLog((1..7).joinToString("<br />") { "Event $it" })

        assertEquals((1..7).map { "Event $it" }, session.battleFeedEntries())
    }

    @Test
    fun liveBattleFeedWindowStaysSmallWhileActivityKeepsTheFullTranscript() {
        val session = BattleSession()
        session.appendShowdownBattleLog((1..40).joinToString("<br />") { "Event $it" })

        assertEquals(40, session.showdownBattleLog().size)
        assertEquals((9..40).map { "Event $it" }, session.battleFeedEntries())
        assertEquals((9..40).map { "Event $it" }, session.activityMessages().takeLast(32))
    }

    @Test
    fun upperBattleFeedSeparatesShowdownBlockMessagesIntoIndividualEvents() {
        val session = BattleSession()
        session.appendShowdownBattleLog("<section><strong>Iron Hands used Thunder Punch!</strong></section><article>It was super effective.</article>")

        assertEquals(
            listOf("Iron Hands used Thunder Punch!", "It was super effective."),
            session.battleFeedEntries()
        )
        assertEquals(
            listOf("Iron Hands used Thunder Punch!", "It was super effective."),
            session.activityMessages().takeLast(2)
        )
    }

    @Test
    fun activityPanelStartsAtTheLatestBattleEntry() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|move|p1a: Pikachu|Thunderbolt|p2a: Eevee",
                "|-damage|p2a: Eevee|50/100"
            )
        )

        session.selectPanel(BattleSession.Panel.ACTIVITY)

        assertEquals(session.activityMessages().lastIndex, session.focusedMessage)
    }

    @Test
    fun lightweightProtocolFeedIncludesOfficialStyleDamageAndHealingMessages() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|move|p1a: Pikachu|Thunderbolt|p2a: Eevee",
                "|-damage|p2a: Eevee|50/100",
                "|-heal|p2a: Eevee|75/100"
            )
        )

        assertTrue(session.battleLog().contains("The opposing Eevee lost 50% of its health!"))
        assertTrue(session.battleLog().contains("The opposing Eevee had its HP restored."))
    }

    @Test
    fun healthMessagesRespectSilentPacketsAndSourceSpecificWording() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p2a: Eevee|Eevee, L50|100/100",
                "|-damage|p2a: Eevee|90/100|[from] brn",
                "|-heal|p2a: Eevee|100/100|[from] item: Leftovers",
                "|-damage|p2a: Eevee|80/100|[silent]"
            )
        )

        assertTrue(session.battleLog().contains("(The opposing Eevee was hurt by its burn!)"))
        assertTrue(session.battleLog().contains("The opposing Eevee restored a little HP using its Leftovers!"))
        assertFalse(session.battleLog().contains("The opposing Eevee lost 20% of its health!"))
    }

    @Test
    fun protocolHintsKeepShowdownParenthesesSeparateFromMessages() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|-hint|This explains the battle rule.",
                "|-message|This is a custom battle message."
            )
        )

        assertTrue(session.battleLog().contains("(This explains the battle rule.)"))
        assertTrue(session.battleLog().contains("This is a custom battle message."))
    }

    @Test
    fun upperBattleFeedRemovesAdjacentDuplicateMessages() {
        val session = BattleSession()

        session.appendShowdownBattleLog("<div>Pikachu used Tackle!</div><div>Pikachu used Tackle!</div>")

        assertEquals(listOf("Pikachu used Tackle!"), session.battleFeedEntries())
    }

    @Test
    fun battleRoomPresenceEventsUseShowdownGroupingAndReplaceTheirFallback() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|j|+Alice",
                "|j|Bob",
                "|l|+Alice",
                "|n|Bobby|Bob"
            )
        )

        assertFalse(session.battleLog().contains("+Alice joined"))
        assertFalse(session.activityMessages().contains("+Alice joined"))
        assertTrue(session.battleLog().contains("+Alice and Bobby joined; +Alice left"))
        assertTrue(session.activityMessages().contains("Bobby renamed from Bob."))
    }

    @Test
    fun battleRoomPresenceGroupingEndsWhenTheNextBattleCommandArrives() {
        val session = BattleSession()

        session.applyProtocolPacket(listOf("|j|Alice", "|request|{\"wait\":true}", "|j|Bob"))

        assertTrue(session.battleLog().contains("Alice and Bob joined"))

        session.applyProtocolLine("|move|p1a: Pikachu|Tackle|p2a: Eevee")
        session.applyProtocolLine("|j|Cara")

        assertTrue(session.battleLog().contains("Alice and Bob joined"))
        assertTrue(session.battleLog().contains("Cara joined"))
        assertFalse(session.battleLog().contains("Alice and Bob and Cara joined"))
    }

    @Test
    fun battleRoomJoinCancelsAnUncommittedLeaveWithoutDuplicatingTheJoin() {
        val session = BattleSession()

        session.applyProtocolPacket(listOf("|j|Alice", "|l|Alice", "|j|Alice"))

        assertEquals(1, session.battleLog().count { it == "Alice joined" })
        assertFalse(session.battleLog().contains("Alice joined; Alice left"))
    }

    @Test
    fun battleRoomRenamesUpdatePresenceIdentityAndReplaceConsecutiveRenameFallbacks() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|j|Alice",
                "|n|Alicia|Alice",
                "|n|Ally|Alicia",
                "|l|Ally"
            )
        )

        assertTrue(session.battleLog().contains("Ally joined; Ally left"))
        assertFalse(session.battleLog().contains("Alice joined; Alicia left"))
        assertFalse(session.battleLog().contains("Alicia renamed from Alice."))
        assertTrue(session.battleLog().contains("Ally renamed from Alicia."))
    }

    @Test
    fun battleRoomRenameWithTheSameUserIdIsNotDisplayed() {
        val session = BattleSession()

        session.applyProtocolLine("|n|Alice@!|Alice")

        assertFalse(session.battleLog().any { it.contains("renamed from") })
    }

    @Test
    fun activityRemovesConsecutiveIdenticalBattleMessages() {
        val session = BattleSession()
        session.appendShowdownBattleLog("<div>It had no effect.</div><div>It had no effect.</div>")

        assertEquals("It had no effect.", session.activityMessages().last())
    }

    @Test
    fun nativeActivityReplacesProtocolFallbacksWithoutRepeatingTheAction() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|move|p1a: Pikachu|Thunderbolt|p2a: Eevee",
                "|-boost|p2a: Eevee|atk|-1"
            )
        )

        session.appendShowdownBattleLog(
            "Pikachu used Thunderbolt!<br />The opposing Eevee's Attack fell!"
        )

        assertEquals(
            listOf(
                "Pikachu used Thunderbolt!",
                "The opposing Eevee's Attack fell!"
            ),
            session.activityMessages().takeLast(2)
        )
    }

    @Test
    fun upperBattleFeedSplitsBreakTagsWithAttributes() {
        val session = BattleSession()
        session.appendShowdownBattleLog("First<br class=\"battle-line\">Second")

        assertEquals(listOf("First", "Second"), session.battleFeedEntries())
    }

    @Test
    fun nativeBattleFeedUsesTheSameUserFacingFilterAsMarkupUpdates() {
        val session = BattleSession()
        session.appendShowdownBattleLog(
            "<div>Battle started!</div>" +
                "<div><small>[12:35] internal timestamp</small></div>" +
                "<div>Register an account to protect your ladder rating!<button>Register</button></div>" +
                "<div>Error parsing: internal parser failure</div>" +
                "<div>Gholdengo used Make It Rain!</div>"
        )

        assertEquals(
            listOf("Battle started!", "Gholdengo used Make It Rain!"),
            session.showdownBattleLog()
        )
        assertEquals(
            listOf("Battle started!", "Gholdengo used Make It Rain!"),
            session.activityMessages().takeLast(2)
        )
    }

    @Test
    fun fallsBackToProtocolBattleEventsWhileNativeTranscriptIsBehind() {
        val session = BattleSession()
        session.applyProtocolLine("|init|battle")
        session.appendShowdownBattleLog("Go! Pikachu!")
        session.applyProtocolLine("|move|p1a: Pikachu|Thunderbolt|p2a: Eevee")

        assertEquals("Pikachu used Thunderbolt!", session.battleFeedEntries().last())

        session.appendShowdownBattleLog("Pikachu used Thunderbolt!")
        session.markNativeBattleLogSynchronized(session.battleLogGeneration())

        assertEquals(
            listOf("Go! Pikachu!", "Pikachu used Thunderbolt!"),
            session.battleFeedEntries()
        )
    }

    @Test
    fun ignoresNativeEntriesAndSynchronizationFromAnOlderBattleGeneration() {
        val session = BattleSession()
        session.applyProtocolLine("|init|battle")
        val oldGeneration = session.battleLogGeneration()
        session.applyProtocolLine("|move|p1a: Pikachu|Thunderbolt|p2a: Eevee")
        session.applyProtocolLine("|init|battle")

        session.appendShowdownBattleLog("Old battle move", oldGeneration)
        session.markNativeBattleLogSynchronized(oldGeneration)

        assertFalse(session.battleFeedEntries().contains("Old battle move"))
        assertEquals(listOf("Battle started."), session.battleFeedEntries())
    }

    @Test
    fun protocolListenersObserveTheGenerationAfterThePacketIsApplied() {
        val session = BattleSession()
        var observedGeneration = -1L
        session.addProtocolListener { observedGeneration = session.battleLogGeneration() }

        session.applyProtocolLine("|init|battle")
        session.applyProtocolLine("|move|p1a: Pikachu|Thunderbolt|p2a: Eevee")

        assertEquals(session.battleLogGeneration(), observedGeneration)
    }

    @Test
    fun nativeBattleStartMessageRemainsVisibleWhenItMatchesTheProtocolPlaceholder() {
        val session = BattleSession()
        session.applyProtocolLine("|init|battle")
        session.appendShowdownBattleLog("Battle started.<br />Go! Pikachu!")
        session.markNativeBattleLogSynchronized(session.battleLogGeneration())

        assertEquals(listOf("Battle started.", "Go! Pikachu!"), session.battleFeedEntries())
    }

    @Test
    fun keepsProtocolEventsVisibleUntilTheMatchingNativeGenerationIsSynchronized() {
        val session = BattleSession()
        session.applyProtocolLine("|init|battle")
        session.appendShowdownBattleLog("Go! Pikachu!")
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|move|p1a: Pikachu|Thunderbolt|p2a: Eevee",
                "|-supereffective|p2a: Eevee"
            )
        )
        session.appendShowdownBattleLog("Pikachu used Thunderbolt!")

        assertTrue(session.battleFeedEntries().contains("Pikachu used Thunderbolt!"))
        assertTrue(session.battleFeedEntries().contains("It's super effective!"))
        assertTrue(session.battleFeedEntries().contains("Go! Pikachu!"))

        session.markNativeBattleLogSynchronized(session.battleLogGeneration())

        assertEquals(
            listOf("Go! Pikachu!", "Pikachu used Thunderbolt!", "It's super effective!"),
            session.battleFeedEntries()
        )
    }

    @Test
    fun nativeHealthWordingReplacesTheProtocolFallbackWithoutDuplicatingIt() {
        val session = BattleSession()
        session.applyProtocolLine("|init|battle")
        session.applyProtocolLine("|-heal|p1a: Pikachu|100/100")
        session.appendShowdownBattleLog("Pikachu restored health!")
        session.markNativeBattleLogSynchronized(session.battleLogGeneration())

        assertEquals(
            listOf("Pikachu restored health!"),
            session.battleFeedEntries()
        )
    }

    @Test
    fun randomDoublesAndTriplesFormatsDoNotRequireSavedTeams() {
        assertTrue(BattleSession.MatchFormat("gen9randomdoublesbattle", "Random Doubles").usesRandomTeams)
        assertTrue(BattleSession.MatchFormat("gen9randomtriplesbattle", "Random Triples").usesRandomTeams)
        assertTrue(BattleSession.MatchFormat("gen9battlefactory", "Battle Factory").usesRandomTeams)
        assertFalse(BattleSession.MatchFormat("gen9doublesou", "Doubles OU").usesRandomTeams)
    }

    @Test
    fun randomFormatDetectionAcceptsWhitespacePaddedIds() {
        assertTrue(BattleSession.MatchFormat(" gen9randombattle ", "Random Battle").usesRandomTeams)
        assertTrue(BattleSession.MatchFormat.usesRandomTeamsFor(" gen9battlefactory "))
        assertTrue(BattleSession.MatchFormat.usesRandomTeams(BattleSession.MatchFormat(" gen9randombattle ", "Random Battle", usesRandomTeams = false)))
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
        session.applyProtocolLine("|request|{\"rqid\":9,\"active\":[{\"moves\":[{\"move\":\"Splash\",\"pp\":10,\"disabled\":true},{\"move\":\"Tackle\",\"pp\":35}]}]}")

        session.confirmSelection()

        assertTrue(decisions.isEmpty())
        assertTrue(session.decisionAvailable)
        assertEquals("Splash is disabled.", session.status)
    }

    @Test
    fun maybeLockedRequestsOfferTheOfficialTestFightRefresh() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"rqid\":19,\"active\":[{\"maybeLocked\":true,\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
        )

        assertTrue(session.canTestFight())
        session.selectTestFightWithTouch()

        assertEquals(listOf("/choose testfight|19"), decisions)
        assertFalse(session.decisionAvailable)
        assertEquals("Checking whether the move is locked…", session.status)
    }

    @Test
    fun ordinaryMoveRequestsDoNotOfferTheTestFightRefresh() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
        )

        assertFalse(session.canTestFight())
    }

    @Test
    fun testFightRefreshKeepsEarlierDoubleBattleChoices() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"rqid\":23,\"targetable\":false,\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]},{\"maybeLocked\":true,\"moves\":[{\"move\":\"Protect\",\"pp\":10}]}]}"
        )

        session.confirmSelection()
        assertTrue(session.canTestFight())
        session.selectTestFightWithTouch()

        assertEquals(listOf("/choose move 1, testfight|23"), decisions)
    }

    @Test
    fun allUnavailableMovesAutomaticallyChooseStruggle() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)

        session.applyProtocolLine(
            "|request|{\"rqid\":10,\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":0},{\"move\":\"Tackle\",\"pp\":35,\"disabled\":true}]}]}"
        )

        assertTrue(session.decisionAvailable)
        assertEquals("Struggle", session.moves().single().name)
        assertEquals(listOf("Protect", "Tackle"), session.playerDetails().moves)
        assertTrue(session.availableGimmicks().isEmpty())
        session.confirmSelection()

        assertEquals(listOf("/choose move 1|10"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun unavailableMoveSlotsBecomeStruggleWithoutSkippingOtherActiveSlots() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)

        session.applyProtocolLine(
            "|request|{\"rqid\":11,\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":0}]},{\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}]}"
        )

        assertEquals("Struggle", session.moves().single().name)
        session.confirmSelection()
        assertEquals("Choose a move for active Pokémon 2/2", session.status)
        session.confirmSelection()

        assertEquals(listOf("/choose move 1, move 1|11"), decisions)
    }

    @Test
    fun officialStruggleRequestRemainsSelectableWithoutReplacingLearnedMoves() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)

        session.applyProtocolLine(
            "|request|{\"rqid\":14,\"active\":[{\"moves\":[{\"move\":\"Struggle\",\"id\":\"struggle\",\"target\":\"randomNormal\",\"disabled\":false}]}],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"100/100\",\"active\":true,\"moves\":[\"protect\",\"fakeout\",\"flareblitz\",\"darkestlariat\"]}]}}"
        )

        assertTrue(session.decisionAvailable)
        assertEquals("Struggle", session.moves().single().name)
        assertEquals("randomNormal", session.moves().single().target)
        assertFalse(session.playerDetails().moves.any { it.equals("Struggle", true) })

        session.confirmSelection()

        assertEquals(listOf("/choose move 1|14"), decisions)
    }

    @Test
    fun hiddenDisabledMoveFlagsRemainSelectable() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"disabled\":\"hidden\"}]}]}"
        )

        assertTrue(session.decisionAvailable)
        assertFalse(session.moves().single().disabled)
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
    fun failedForcedSwitchSubmissionRestoresPositionalChoices() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":43,\"forceSwitch\":[false,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )

        session.selectTeamWithTouch(2)
        session.handleDecisionSendFailure()
        session.selectTeamWithTouch(2)

        assertEquals(listOf("/choose pass, switch 3|43", "/choose pass, switch 3|43"), decisions)
    }

    @Test
    fun rejectedSingleForcedSwitchClearsThePreviousChoice() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":44,\"forceSwitch\":[true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )

        session.selectTeamWithTouch(1)
        session.applyProtocolLine("|error|That switch is invalid.")
        session.selectTeamWithTouch(1)

        assertEquals(listOf("/choose switch 2|44", "/choose switch 2|44"), decisions)
    }

    @Test
    fun sentForcedSwitchPreservesPositionalMaskAfterReconnect() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener { decisions += it }
        session.setLiveBattleActive(true)
        session.applyProtocolLine(
            "|request|{\"rqid\":45,\"forceSwitch\":[false,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )

        session.selectTeamWithTouch(2)
        session.applyProtocolLine("|sentchoice|pass, switch 3")

        assertEquals(listOf("/choose pass, switch 3|45"), decisions)
        assertFalse(session.decisionAvailable)
        assertTrue(session.canCancelChoice())
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
        assertTrue(session.isBattleParticipant())
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
    fun sparseActiveRequestsKeepTheProtocolSlotAndPartyMoveDetailsAligned() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener(decisions::add)
        session.applyProtocolLine(
            "|request|{\"rqid\":46,\"active\":[null,{\"moves\":[{\"move\":\"Helping Hand\",\"pp\":10,\"target\":\"adjacentAlly\"}]}],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":false},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )

        assertEquals(listOf("p1b"), session.playerActiveCombatants().map { it.slot })
        assertEquals("Naganadel", session.playerDetails().name)
        assertEquals(listOf("Helping Hand"), session.teamMemberDetails(1).moves)
        assertEquals("Choose a move for active Pokémon 2/2", session.status)

        session.confirmSelection()

        assertEquals(listOf("/choose pass, move 1|46"), decisions)
    }

    @Test
    fun requestSyncUsesTheExplicitActiveSlotsInsteadOfExtraSideFlags() {
        val session = BattleSession()
        session.applyProtocolLine("|gametype|doubles")
        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]},{\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}],\"side\":{\"pokemon\":[{\"ident\":\"p1: First\",\"details\":\"Pikachu, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p1: Second\",\"details\":\"Eevee, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p1: Extra\",\"details\":\"Mew, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )

        assertEquals(listOf("p1a", "p1b"), session.playerActiveCombatants().map { it.slot })
        assertEquals(listOf("First", "Second"), session.playerActiveCombatants().map { it.name })
    }

    @Test
    fun requestWithNoAuthoritativeActiveSlotsClearsPreviousCards() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|gametype|doubles",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100",
                "|switch|p1b: Eevee|Eevee, L50|100/100"
            )
        )
        session.applyProtocolLine(
            "|request|{\"active\":[],\"side\":{\"pokemon\":[{\"ident\":\"p1: Pikachu\",\"details\":\"Pikachu, L50\",\"condition\":\"100/100\"},{\"ident\":\"p1: Eevee\",\"details\":\"Eevee, L50\",\"condition\":\"100/100\"}]}}"
        )

        assertTrue(session.playerActiveCombatants().isEmpty())
    }

    @Test
    fun requestSideIdentReorientsTheLocalBattleSlot() {
        val session = BattleSession()
        session.setLocalUsername("LOCAL")
        session.applyProtocolLine(
            "|request|{\"active\":[{\"moves\":[{\"move\":\"Protect\",\"pp\":10}]},{\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}],\"side\":{\"pokemon\":[{\"ident\":\"p2: First\",\"details\":\"Pikachu, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p2: Second\",\"details\":\"Eevee, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )

        assertEquals("p2", session.battlePlayerSlot())
        assertEquals(listOf("p2a", "p2b"), session.playerActiveCombatants().map { it.slot })
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

        assertEquals(listOf("/choose move 1 +2, move 1|13"), decisions)
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
        assertEquals(listOf("+2"), session.targetOptions().map { it.choice })
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
            session.moveFocus(
                index % SwitchTeamLayout.COLUMNS - session.focusedTeam % SwitchTeamLayout.COLUMNS,
                index / SwitchTeamLayout.COLUMNS - session.focusedTeam / SwitchTeamLayout.COLUMNS
            )
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
    fun teamPreviewKeepsDirectMoveEntryPointsOnTheReplacementGrid() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"rqid\":19,\"teamPreview\":true,\"maxChosenTeamSize\":2,\"side\":{\"pokemon\":[{\"ident\":\"p1: Pikachu\",\"details\":\"Pikachu, L50\",\"condition\":\"100/100\"},{\"ident\":\"p1: Eevee\",\"details\":\"Eevee, L50\",\"condition\":\"100/100\"}]}}"
        )

        session.focusMove(0)
        session.selectMoveWithTouch(0)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectPanel(BattleSession.Panel.ACTIVITY)
        session.cyclePanel(1)

        assertEquals(BattleSession.Panel.TEAM, session.panel)
        assertTrue(session.decisionAvailable)
    }

    @Test
    fun teamPreviewUsesTheMaximumWhenBothRequestSizesArePresent() {
        val session = BattleSession()
        session.applyProtocolLine("|request|{\"rqid\":18,\"teamPreview\":true,\"chosenTeamSize\":6,\"maxChosenTeamSize\":2}")

        assertEquals(2, session.teamPreviewRequiredSize())
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
    fun freeForAllKeepsEveryOtherParticipantOnTheOpponentSide() {
        val session = BattleSession()
        session.setLocalUsername("ALLY")
        session.applyProtocolPacket(
            listOf(
                "|gametype|freeforall",
                "|player|p1|ALLY",
                "|player|p2|FOE",
                "|player|p3|FOE2",
                "|player|p4|FOE3",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p3a: Druddigon|Druddigon, L50|100/100",
                "|switch|p4a: Landorus|Landorus, L50|100/100"
            )
        )

        assertEquals(listOf("Incineroar"), session.playerActiveCombatants().map { it.name })
        assertEquals(listOf("Tapu Koko", "Druddigon", "Landorus"), session.opponentActiveCombatants().map { it.name })
        assertTrue(session.battleLog().contains("Battle type: Free-for-all."))
    }

    @Test
    fun freeForAllOffersEveryOpponentAsAnExplicitTarget() {
        val session = BattleSession()
        session.setLocalUsername("ALLY")
        session.applyProtocolPacket(
            listOf(
                "|gametype|freeforall",
                "|player|p1|ALLY",
                "|player|p2|FOE",
                "|player|p3|FOE2",
                "|player|p4|FOE3",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p3a: Druddigon|Druddigon, L50|100/100",
                "|switch|p4a: Landorus|Landorus, L50|100/100",
                "|request|{\"targetable\":true,\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"target\":\"normal\"}]}]}"
            )
        )

        assertEquals(listOf("+1", "-2", "+2"), session.targetOptions().map { it.choice })
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
    fun replayPreparationUsesALoadingStateUntilBattleProtocolArrives() {
        val session = BattleSession()

        session.prepareForReplay()

        assertFalse(session.hasBattleProtocolTranscript())
        assertEquals(listOf("Loading replay…"), session.battleLog())
        assertEquals("Loading replay…", session.status)

        session.applyProtocolLine("|init|battle")

        assertTrue(session.hasBattleProtocolTranscript())
        assertEquals(listOf("Battle started."), session.battleLog())
    }

    @Test
    fun newBattleStartsOnTheFightPanelAfterLeavingTheLobbyMenu() {
        val session = BattleSession()
        session.prepareForLobby()
        session.selectPanel(BattleSession.Panel.MENU)

        session.applyProtocolLine("|init|battle")

        assertEquals(BattleSession.Panel.MOVES, session.panel)
    }

    @Test
    fun liveBattleActivationLeavesTheLobbyMenuOnTheFightPanel() {
        val session = BattleSession()
        session.prepareForLobby()

        session.setLiveBattleActive(true)

        assertEquals(BattleSession.Panel.MOVES, session.panel)
        assertEquals("Battle starting", session.status)
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
        assertEquals(3, session.focusedTeam)
        session.selectPanel(BattleSession.Panel.ACTIVITY)
        assertEquals(session.activityMessages().lastIndex, session.focusedMessage)
        session.moveFocus(0, 1)
        assertEquals(session.activityMessages().lastIndex, session.focusedMessage)
        session.selectPanel(BattleSession.Panel.MENU)
        session.moveFocus(0, 1)

        assertEquals(3, session.focusedMenuItem)
    }

    @Test
    fun controllerNavigationMatchesTheTwoColumnSwitchGrid() {
        val session = BattleSession()

        session.selectPanel(BattleSession.Panel.TEAM)
        session.moveFocus(0, 1)
        assertEquals(2, session.focusedTeam)
        session.moveFocus(1, 0)
        assertEquals(3, session.focusedTeam)
        session.moveFocus(0, 1)
        assertEquals(5, session.focusedTeam)
        session.moveFocus(-1, 0)
        assertEquals(4, session.focusedTeam)
    }

    @Test
    fun touchSelectsAndSendsAMoveWithoutAddingAnOptimisticBattleLogEntry() {
        val session = BattleSession()

        session.selectMoveWithTouch(2)
        assertTrue(session.chatMessages().last().contains("/choose move 3"))
        assertEquals("Move sent: Darkest Lariat", session.status)
        assertFalse(session.battleLog().any { it.contains("chose Darkest Lariat") })

        session.applyProtocolLine("|move|p1a: Incineroar|Darkest Lariat|p2a: Tapu Koko")

        assertEquals("Incineroar used Darkest Lariat!", session.latestBattleEvent)
        assertEquals("Incineroar used Darkest Lariat!", session.battleLog().last())
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
    fun modernXySpritesAreTheDefaultAndRemainEnabled() {
        val session = BattleSession()

        assertEquals(BattleSession.SpriteStyle.MODERN_3D, session.spriteStyle)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(8)
        session.confirmSelection()

        assertEquals(BattleSession.SpriteStyle.MODERN_3D, session.spriteStyle)
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
    fun gimmickChoicesUseOfficialDynamaxAndTerastallizeSuffixes() {
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
            listOf("/choose move 1 max", "/choose move 1 terastallize"),
            decisions
        )
    }

    @Test
    fun targetedGimmickChoicesPlaceTheTargetBeforeTheOfficialSuffix() {
        val cases = listOf(
            BattleSession.BattleGimmick.Z_POWER to "zmove",
            BattleSession.BattleGimmick.MEGA_EVOLUTION to "mega",
            BattleSession.BattleGimmick.DYNAMAX to "max",
            BattleSession.BattleGimmick.TERASTALLIZATION to "terastallize"
        )

        cases.forEachIndexed { index, (gimmick, suffix) ->
            val decisions = mutableListOf<String>()
            val session = BattleSession()
            session.addDecisionListener(decisions::add)
            session.applyProtocolLine(
                "|request|{\"rqid\":${40 + index},\"active\":[{\"canZMove\":[{}],\"canMegaEvo\":true,\"canDynamax\":true,\"canTerastallize\":\"Fire\",\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"target\":\"normal\"}]},{\"moves\":[{\"move\":\"Protect\",\"pp\":10,\"target\":\"self\"}]}]}"
            )

            session.selectGimmick(gimmick)
            session.selectTargetWithTouch(0)
            session.confirmSelection()

            assertEquals(
                listOf("/choose move 1 +1 $suffix, move 1|${40 + index}"),
                decisions
            )
        }
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
    fun stringGigantamaxRequestsUseTheOfficialGimmickLabel() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"active\":[{\"canDynamax\":true,\"maxMoves\":{\"gigantamax\":\"G-Max Drum Solo\",\"maxMoves\":[{\"move\":\"G-Max Drum Solo\"}]},\"moves\":[{\"move\":\"Drum Beating\",\"pp\":10}]}]}"
        )

        assertEquals("Gigantamax", session.gimmickLabel(BattleSession.BattleGimmick.DYNAMAX))
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

        assertEquals(listOf("+1", "+2", "-2"), session.targetOptions().map { it.choice })
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

        assertEquals(listOf("+2", "+3"), session.targetOptions().map { it.choice })

        session.applyProtocolLine(
            "|request|{\"targetable\":true,\"active\":[null,{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"target\":\"normal\"}]},null]}"
        )

        assertEquals(listOf("+1", "+2", "+3"), session.targetOptions().map { it.choice })

        session.applyProtocolLine(
            "|request|{\"targetable\":true,\"active\":[null,null,{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"target\":\"adjacentFoe\"}]}]}"
        )

        assertEquals(listOf("+1", "+2"), session.targetOptions().map { it.choice })

        session.applyProtocolLine(
            "|request|{\"targetable\":true,\"active\":[{\"moves\":[{\"move\":\"Rock Slide\",\"pp\":10,\"target\":\"allAdjacentFoes\"}]},null,null]}"
        )

        assertTrue(session.targetOptions().isEmpty())
    }

    @Test
    fun triplesExposeAndSubmitTheFifthAnyTarget() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolPacket(
            listOf(
                "|gametype|triples",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Naganadel|Naganadel, L50|100/100",
                "|switch|p1c: Mimikyu|Mimikyu, L50|100/100",
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p2b: Druddigon|Druddigon, L50|100/100",
                "|switch|p2c: Landorus|Landorus, L50|100/100",
                "|request|{\"rqid\":45,\"targetable\":true,\"active\":[{\"moves\":[{\"move\":\"Trick\",\"pp\":10,\"target\":\"any\"}]},null,null]}"
            )
        )

        assertEquals(listOf("+1", "+2", "+3", "-2", "-3"), session.targetOptions().map { it.choice })
        session.selectTargetWithTouch(4)

        assertEquals(listOf("/choose move 1 -3, pass, pass|45"), decisions)
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
    fun activeDetailsFollowTheSelectedDoubleBattleSlot() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|ADRIAN||",
                "|player|p2|OPPONENT||",
                "|switch|p1a: Incineroar|Incineroar, L50|100/100",
                "|switch|p1b: Naganadel|Naganadel, L50|72/100 brn",
                "|-ability|p1b: Naganadel|Beast Boost",
                "|-item|p1b: Naganadel|Dragonium Z"
            )
        )

        val details = session.detailsForActiveCombatant(true, "p1b")

        assertEquals("Naganadel", details?.name)
        assertEquals("Naganadel", details?.species)
        assertEquals("72/100 brn", details?.hp)
        assertEquals("Dragonium Z", details?.item)
        assertEquals("Beast Boost", details?.ability)
    }

    @Test
    fun revealedPackedAbilitySlotsUseSpeciesAwareNames() {
        val session = BattleSession()
        session.setAbilitySlotResolver { species, ability ->
            if (species == "Pikachu" && ability == "H") "Lightning Rod" else ability
        }
        val packed = listOf("Pikachu", "Pikachu", "", "H", "", "", "", "", "", "", "", "").joinToString("|")

        session.applyProtocolLine("|showteam|p2|$packed")

        assertEquals("Lightning Rod", session.opponentPartyDetails().single().ability)
    }

    @Test
    fun activeDetailsDoNotCopyLeadMetadataToAnUnknownPartner() {
        val session = BattleSession()

        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Tyranitar|Tyranitar, L50|100/100",
                "|switch|p1b: Primarina|Primarina, L50|84/100"
            )
        )

        val details = session.detailsForActiveCombatant(true, "p1b")

        assertEquals("Unknown ability", details?.ability)
        assertEquals("Unknown item", details?.item)
        assertTrue(details?.moves.orEmpty().isEmpty())
        assertTrue(details?.stats.orEmpty().isEmpty())
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
    fun speciesNamesStartingWithLDoNotBecomeActiveLevels() {
        val session = BattleSession()

        session.applyProtocolLine("|switch|p2b: Lurantis|Lurantis, F|100/100")

        assertEquals("50", session.opponentActiveCombatants().single().level)
        assertEquals("♀", session.opponentActiveCombatants().single().gender)
    }

    @Test
    fun nicknameIdentifiersUpdateTheActiveSpeciesDetails() {
        val session = BattleSession()
        session.applyProtocolLine("|switch|p1a: Sparky|Pikachu, L50|100/100")

        session.applyProtocolLine("|-item|p1a: Sparky|Light Ball")
        session.applyProtocolLine("|-ability|p1a: Sparky|Static")

        assertEquals("Sparky", session.playerDetails().name)
        assertEquals("Pikachu", session.playerDetails().species)
        assertEquals("Light Ball", session.playerDetails().item)
        assertEquals("Static", session.playerDetails().ability)
    }

    @Test
    fun requestSyncKeepsNicknameAndSpeciesAsSeparateBattleIdentityFields() {
        val session = BattleSession()
        session.applyProtocolLine(
            "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Sparky\",\"details\":\"Pikachu, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )

        assertEquals("Sparky", session.playerDetails().name)
        assertEquals("Pikachu", session.playerDetails().species)
        assertEquals("Sparky", session.playerActiveCombatants().single().name)
        assertEquals("Pikachu", session.playerActiveCombatants().single().species)
        assertEquals("Sparky", session.teamMemberDetails(0).name)
        assertEquals("Pikachu", session.teamMemberDetails(0).species)
    }

    @Test
    fun switchPacketsKeepVisibleNicknamesOnBothSides() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p1a: Sparky|Pikachu, L50|100/100",
                "|switch|p2a: Phantom|Dragapult, L50|100/100"
            )
        )

        assertEquals("Sparky", session.playerActiveCombatants().single().name)
        assertEquals("Pikachu", session.playerActiveCombatants().single().species)
        assertEquals("Phantom", session.opponentActiveCombatants().single().name)
        assertEquals("Dragapult", session.opponentActiveCombatants().single().species)
    }

    @Test
    fun activeCombatantsKeepSpeciesSeparateFromNicknames() {
        val session = BattleSession()
        val packed = ShowdownTeamCodec.pack(listOf(ShowdownTeamSet(nickname = "Sparky", species = "Pikachu")))
        session.applyProtocolLine("|showteam|p2|$packed")
        session.applyProtocolLine("|switch|p2a: Sparky|Pikachu, L50|100/100")

        assertEquals("Sparky", session.opponentActiveCombatants().single().name)
        assertEquals("Pikachu", session.opponentActiveCombatants().single().species)
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
        assertEquals("Alcremie", BattleSession.displayPokemonName("Alcremie-Caramel-Swirl", "Alcremie-Mint-Cream"))
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

        assertFalse(session.battleLog().any { it.contains("chose Fake Out") })

        session.applyProtocolLine("|move|p1a: Alcremie-Caramel-Swirl|Fake Out|p2a: Tapu Koko")

        assertEquals("Alcremie used Fake Out!", session.latestBattleEvent)
        assertEquals("Alcremie used Fake Out!", session.battleLog().last())
    }

    @Test
    fun updatePokeReplacesAnActiveFormWithoutDroppingItsPartyState() {
        val session = BattleSession()
        session.setPokemonTypeResolver { species ->
            when (species) {
                "Zoroark" -> listOf("DARK")
                "Zoroark-Hisui" -> listOf("NORMAL", "GHOST")
                else -> null
            }
        }

        session.applyProtocolLine(
            "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Zoro\",\"details\":\"Zoroark, L50\",\"condition\":\"100/100\",\"active\":true}]}}"
        )
        session.applyProtocolLine("|updatepoke|p1: Zoro|Zoroark-Hisui, L50")

        assertEquals("Zoro", session.playerActiveCombatants().single().name)
        assertEquals("Zoroark-Hisui", session.playerActiveCombatants().single().species)
        assertEquals(listOf("NORMAL", "GHOST"), session.playerActiveCombatants().single().types)
        assertEquals("Zoroark-Hisui", session.playerDetails().species)
    }

    @Test
    fun replacePacketsRevealTheActualSpeciesWithoutDiscardingAPlayerNickname() {
        val session = BattleSession()
        session.setLocalUsername("ADRIAN")
        session.applyProtocolPacket(
            listOf(
                "|player|p1|ADRIAN|",
                "|player|p2|OPPONENT|",
                "|request|{\"side\":{\"pokemon\":[{\"ident\":\"p1: Zoro\",\"details\":\"Zoroark, L50\",\"condition\":\"100/100\",\"active\":true}]}}",
                "|switch|p1a: Zoro|Pikachu, L50|100/100",
                "|replace|p1a: Zoro|Zoroark, L50|100/100"
            )
        )

        assertEquals("Zoro", session.playerActiveCombatants().single().name)
        assertEquals("Zoroark", session.playerActiveCombatants().single().species)
        assertEquals("Zoro", session.playerDetails().name)
        assertEquals("Zoroark", session.playerDetails().species)
        assertTrue(session.battleLog().last().contains("was revealed as Zoroark"))
    }

    @Test
    fun replacePacketsClearStaleOpponentIdentityDetails() {
        val session = BattleSession()
        session.applyProtocolPacket(
            listOf(
                "|switch|p2a: Zoro|Pikachu, L50|100/100",
                "|-ability|p2a: Zoro|Static",
                "|-item|p2a: Zoro|Light Ball",
                "|replace|p2a: Zoro|Zoroark, L50|100/100"
            )
        )

        val revealed = session.opponentActiveCombatants().single()
        assertEquals("Zoro", revealed.name)
        assertEquals("Zoroark", revealed.species)
        assertEquals("Zoro", session.opponentDetails().name)
        assertEquals("Zoroark", session.opponentDetails().species)
        assertEquals("Unknown ability", session.opponentDetails().ability)
        assertEquals("Unknown item", session.opponentDetails().item)
    }

    @Test
    fun customEndTerastallizeRestoresTheOriginalTypes() {
        val session = BattleSession()
        session.applyProtocolLine("|switch|p1a: Incineroar|Incineroar, L50|100/100")
        session.applyProtocolLine("|-terastallize|p1a: Incineroar|WATER")

        assertEquals(listOf("WATER"), session.playerActiveCombatants().single().types)

        session.applyProtocolLine("|custom|-endterastallize|p1a: Incineroar")

        assertEquals(listOf("FIRE", "DARK"), session.playerActiveCombatants().single().types)
        assertEquals(listOf("FIRE", "DARK"), session.playerDetails().types)
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
        assertEquals("ADRIAN won the battle.", session.battleResult())
        assertTrue(session.isBattleFinished())
        assertTrue(feedback.any { it.type == BattleSession.FeedbackType.POKEMON_CRY && it.actor == "Incineroar" })
        assertTrue(feedback.any { it.type == BattleSession.FeedbackType.MOVE && it.move == "Flare Blitz" })
        assertTrue(feedback.any { it.type == BattleSession.FeedbackType.HIT && it.impact == BattleSession.HitImpact.SUPER_EFFECTIVE_CRITICAL })

        session.applyProtocolLine("|request|{\"rqid\":28,\"active\":[{\"moves\":[{\"move\":\"Darkest Lariat\",\"type\":\"Dark\",\"pp\":10}]}]}")
        session.confirmSelection()

        assertTrue(session.chatMessages().last().contains("/choose move 1|28"))
    }

    @Test
    fun terminalBattleResultSurvivesLaterConnectionStatusUpdates() {
        val session = BattleSession()

        session.applyProtocolLine("|win|ADRIAN")
        session.setConnectionStatus("Replay: [Gen 9] Random Battle ADRIAN vs. GLADION")

        assertEquals("ADRIAN won the battle.", session.battleResult())
        assertEquals("Replay: [Gen 9] Random Battle ADRIAN vs. GLADION", session.status)
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
    fun indirectDamageDoesNotEmitAHitFeedbackEvent() {
        val session = BattleSession()
        val feedback = mutableListOf<BattleSession.BattleFeedback>()
        session.addFeedbackListener { feedback += it }

        session.applyProtocolPacket(
            listOf(
                "|-damage|p2a: Tapu Koko|90/100 brn|[from] status: brn",
                "|-damage|p2a: Tapu Koko|80/100|[from] item: Life Orb"
            )
        )

        assertFalse(feedback.any { it.type == BattleSession.FeedbackType.HIT })
    }

    @Test
    fun directSetHpDamageEmitsOneHitFeedbackEvent() {
        val session = BattleSession()
        val feedback = mutableListOf<BattleSession.BattleFeedback>()
        session.addFeedbackListener { feedback += it }

        session.applyProtocolPacket(
            listOf(
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|move|p1a: Incineroar|Pain Split|p2a: Tapu Koko",
                "|-sethp|p2a: Tapu Koko|40/100"
            )
        )

        assertEquals(1, feedback.count { it.type == BattleSession.FeedbackType.HIT })
    }

    @Test
    fun healingSetHpDoesNotEmitHitFeedbackEvent() {
        val session = BattleSession()
        val feedback = mutableListOf<BattleSession.BattleFeedback>()
        session.addFeedbackListener { feedback += it }

        session.applyProtocolPacket(
            listOf(
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|40/100",
                "|-sethp|p2a: Tapu Koko|100/100"
            )
        )

        assertFalse(feedback.any { it.type == BattleSession.FeedbackType.HIT })
    }

    @Test
    fun sourcedSetHpDamageDoesNotEmitHitFeedbackEvent() {
        val session = BattleSession()
        val feedback = mutableListOf<BattleSession.BattleFeedback>()
        session.addFeedbackListener { feedback += it }

        session.applyProtocolPacket(
            listOf(
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|-sethp|p2a: Tapu Koko|40/100|[from] move: Pain Split"
            )
        )

        assertFalse(feedback.any { it.type == BattleSession.FeedbackType.HIT })
    }

    @Test
    fun multiTargetSetHpUpdatesEveryTargetAndEmitsOneHitPerTarget() {
        val session = BattleSession()
        val feedback = mutableListOf<BattleSession.BattleFeedback>()
        session.addFeedbackListener { feedback += it }

        session.applyProtocolPacket(
            listOf(
                "|switch|p2a: Tapu Koko|Tapu Koko, L50|100/100",
                "|switch|p2b: Garchomp|Garchomp, L50|100/100",
                "|-sethp|p2a: Tapu Koko|40/100|p2b: Garchomp|60/100"
            )
        )

        assertEquals("40/100", session.opponentHp)
        assertEquals("60/100", session.opponentActiveCombatants().first { it.slot == "p2b" }.hp)
        assertEquals(2, feedback.count { it.type == BattleSession.FeedbackType.HIT })
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
    fun forcedSwitchCardsIdentifyTheActivePokemonAsUnavailable() {
        val session = BattleSession()

        session.applyProtocolLine("|request|{\"rqid\":34,\"forceSwitch\":[true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}")

        assertEquals(1, session.focusedTeam)
        assertEquals("Fainted", session.teamCardStatus(0))
        assertEquals("Switch in", session.teamCardStatus(1))

        session.selectPanel(BattleSession.Panel.MENU)
        session.focusMove(0)
        session.selectMoveWithTouch(0)

        assertEquals(BattleSession.Panel.TEAM, session.panel)
    }

    @Test
    fun selectedForcedSwitchCardsShowTheirSelectionProgress() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"rqid\":35,\"forceSwitch\":[true,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\"},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\"},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"100/100\"}]}}"
        )

        session.selectTeamWithTouch(1)

        assertEquals("Selected 1/2", session.teamCardStatus(1))
        assertEquals("Switch in", session.teamCardStatus(2))
    }

    @Test
    fun submittedForcedSwitchCardsShowWaitingStateForUnselectedPokemon() {
        val session = BattleSession()

        session.applyProtocolLine(
            "|request|{\"rqid\":36,\"forceSwitch\":[true,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"100/100\"},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\"},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"100/100\"},{\"ident\":\"p1: Landorus\",\"details\":\"Landorus, L50\",\"condition\":\"100/100\"}]}}"
        )

        session.selectTeamWithTouch(1)
        session.selectTeamWithTouch(2)

        assertFalse(session.decisionAvailable)
        assertEquals("Selected 2/2", session.teamCardStatus(1))
        assertEquals("Waiting", session.teamCardStatus(3))
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
        session.moveFocus(0, 1)
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

        assertEquals(listOf("/choose pass, pass|39"), decisions)
        assertFalse(session.decisionAvailable)
    }

    @Test
    fun singleForcedSwitchPreservesLeadingPass() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":41,\"forceSwitch\":[false,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )

        session.selectTeamWithTouch(2)

        assertEquals(listOf("/choose pass, switch 3|41"), decisions)
    }

    @Test
    fun multipleForcedSwitchesPreserveInactivePositions() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":42,\"forceSwitch\":[true,false,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"100/100\",\"active\":true},{\"ident\":\"p1: Tapu Koko\",\"details\":\"Tapu Koko, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false},{\"ident\":\"p1: Landorus\",\"details\":\"Landorus, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )

        session.selectTeamWithTouch(3)
        session.selectTeamWithTouch(4)

        assertEquals(listOf("/choose switch 4, pass, switch 5|42"), decisions)
    }

    @Test
    fun forcedSwitchesPadMissingReplacementsWithPasses() {
        val decisions = mutableListOf<String>()
        val session = BattleSession()
        session.addDecisionListener { decisions += it }
        session.applyProtocolLine(
            "|request|{\"rqid\":40,\"forceSwitch\":[true,true],\"side\":{\"pokemon\":[{\"ident\":\"p1: Incineroar\",\"details\":\"Incineroar, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Mimikyu\",\"details\":\"Mimikyu, L50\",\"condition\":\"0 fnt\",\"active\":true},{\"ident\":\"p1: Naganadel\",\"details\":\"Naganadel, L50\",\"condition\":\"100/100\",\"active\":false}]}}"
        )

        session.moveFocus(0, 1)
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
    fun battleMenuOpensBattleControls() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }
        session.applyProtocolLine("|init|battle")

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(13)
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
    fun battleMenuPreservesTheNoLiveTimerStatus() {
        val session = BattleSession()
        session.addClientActionListener {
            if (it == BattleSession.ClientAction.TOGGLE_BATTLE_TIMER) {
                session.setConnectionStatus("There is no live battle timer to change.")
            }
        }

        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(14)
        session.confirmSelection()

        assertEquals("There is no live battle timer to change.", session.status)
    }

    @Test
    fun nativeBattleMarkupUpdatesReplacePreviousEntries() {
        val session = BattleSession()

        session.replaceShowdownBattleMarkup("notice", "<b>Queue open</b>")
        session.replaceShowdownBattleMarkup("notice", "<b>Queue closed</b>")

        assertFalse(session.showdownBattleLog().contains("Queue open"))
        assertEquals(listOf("Queue closed"), session.showdownBattleLog())
        assertEquals(1, session.activityMessages().count { it == "Queue closed" })
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
            announcer = true
        )

        assertFalse(session.soundEffectsEnabled)
        assertTrue(session.musicEnabled)
        assertFalse(session.hapticsEnabled)
        assertTrue(session.announcerEnabled)
        assertEquals(BattleSession.SpriteStyle.MODERN_3D, session.spriteStyle)
    }

    @Test
    fun announcerMenuIsOffByDefaultAndTogglesWithSettings() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.selectPanel(BattleSession.Panel.MENU)
        assertFalse(session.announcerEnabled)
        assertEquals("PBR announcer off", session.menuItems()[7])

        session.selectMenuItem(7)
        session.confirmSelection()

        assertTrue(session.announcerEnabled)
        assertEquals("PBR announcer on", session.menuItems()[7])
        assertEquals("PBR announcer enabled.", session.status)
        assertEquals(listOf(BattleSession.ClientAction.SETTINGS_CHANGED), actions)
    }

    @Test
    fun spriteMenuNamesTheShowdownSpriteFamilyThatWillBeUsed() {
        val session = BattleSession()

        session.selectPanel(BattleSession.Panel.MENU)
        assertEquals("Sprite style HD-first animated", session.menuItems()[8])

        session.selectMenuItem(8)
        session.confirmSelection()

        assertEquals(BattleSession.SpriteStyle.MODERN_3D, session.spriteStyle)
        assertEquals("Sprite style HD-first animated", session.menuItems()[8])
        assertEquals("HD-first animated sprite style enabled.", session.status)
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
    fun serverFormatCatalogPreservesSearchAndChallengeCapabilities() {
        val formats = BattleSession.parseServerFormats(
            "|formats|gen9randombattle,[Gen 9] Random Battle,4f|gen9multirandombattle,[Gen 9] Multi Random Battle,5|gen9ou,[Gen 9] OU,e"
        )

        assertTrue(formats[0].canSearch)
        assertTrue(formats[0].canChallenge)
        assertFalse(formats[1].canSearch)
        assertTrue(formats[1].canChallenge)
        assertTrue(formats[2].canSearch)
        assertTrue(formats[2].canChallenge)
    }

    @Test
    fun unavailableSavedFormatFallsBackToTheServerDefault() {
        val session = BattleSession()
        session.setMatchFormat(BattleSession.MatchFormat("gen9multirandombattle", "[Gen 9] Multi Random Battle"))

        session.applyServerFormats(
            listOf("|formats|gen9randombattle,[Gen 9] Random Battle,4f|gen9ou,[Gen 9] OU,e")
        )

        assertEquals("gen9randombattle", session.matchFormat.id)
        assertEquals(listOf("gen9randombattle", "gen9ou"), session.availableMatchFormats().map { it.id })
    }

    @Test
    fun knownSavedChampionsDoublesFormatSurvivesServerFormatRefresh() {
        val session = BattleSession()
        session.setMatchFormat(BattleSession.MatchFormat.GEN9_CHAMPIONS_RANDOM_DOUBLES)

        session.applyServerFormats(
            listOf("|formats|gen9randombattle,[Gen 9] Random Battle,4f|gen9ou,[Gen 9] OU,e")
        )

        assertEquals("gen9championsrandomdoublesbattle", session.matchFormat.id)
        assertTrue(session.matchFormat.usesRandomTeams)
        assertTrue(session.availableMatchFormats().any { it.id == session.matchFormat.id })
    }

    @Test
    fun challengeOnlySavedFormatFallsBackEvenWhenTheServerAdvertisesIt() {
        val session = BattleSession()
        session.setMatchFormat(BattleSession.MatchFormat("gen9multirandombattle", "[Gen 9] Multi Random Battle", canSearch = false))

        session.applyServerFormats(
            listOf("|formats|gen9multirandombattle,[Gen 9] Multi Random Battle,5|gen9randombattle,[Gen 9] Random Battle,4f")
        )

        assertEquals("gen9randombattle", session.matchFormat.id)
        assertFalse(session.availableMatchFormats().first().canSearch)
    }

    @Test
    fun selectedChallengeOnlyFormatSurvivesLaterFormatRefreshes() {
        val session = BattleSession()
        session.applyServerFormats(
            listOf("|formats|gen9randombattle,[Gen 9] Random Battle,4f|gen9multirandombattle,[Gen 9] Multi Random Battle,5")
        )
        session.setMatchFormat(session.availableMatchFormats().last())

        session.applyServerFormats(
            listOf("|formats|gen9randombattle,[Gen 9] Random Battle,4f|gen9multirandombattle,[Gen 9] Multi Random Battle,5")
        )

        assertEquals("gen9multirandombattle", session.matchFormat.id)
        assertFalse(session.matchFormat.canSearch)
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
    fun spectatorModeKeepsTheBattleReadOnlyWithoutCallingItAReplay() {
        val session = BattleSession()

        session.applyProtocolPacket(listOf("|init|battle", "|player|p1|MISTY", "|player|p2|GLADION"))
        session.setLiveBattleActive(true)
        session.setSpectatorMode(true)

        assertTrue(session.isSpectatorMode())
        assertFalse(session.isReplayMode())
        assertFalse(session.decisionAvailable)
        assertEquals("Leave battle", session.menuItems()[3])
        assertEquals("Battle controls", session.menuItems()[13])
    }

    @Test
    fun spectatorBattleInitializationKeepsWatchingStatus() {
        val session = BattleSession()
        session.setSpectatorMode(true)

        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|switch|p1a: Pikachu|Pikachu, L50|100/100"
            )
        )

        assertEquals("Spectating battle", session.status)
        assertEquals("Go! Pikachu!", session.battleLog().last())
        assertEquals("Go! Pikachu!", session.latestBattleFeedEntry())
    }

    @Test
    fun recoveredSpectatorMenuLeavesTheBattleInsteadOfChallenging() {
        val session = BattleSession()
        val actions = mutableListOf<BattleSession.ClientAction>()
        session.addClientActionListener { actions += it }

        session.applyProtocolPacket(listOf("|init|battle", "|player|p1|MISTY", "|player|p2|GLADION"))
        session.setLiveBattleActive(true)
        session.setSpectatorMode(true)
        session.selectPanel(BattleSession.Panel.MENU)
        session.selectMenuItem(3)
        session.confirmSelection()

        assertEquals("Leave battle", session.menuItems()[3])
        assertEquals(listOf(BattleSession.ClientAction.LEAVE_BATTLE), actions)
    }

    @Test
    fun restoredSideDoesNotMakeAReadOnlySpectatorACombatant() {
        val session = BattleSession()

        session.restoreBattlePlayerSlot("p2")
        session.setLiveBattleActive(true)
        session.setSpectatorMode(true)

        assertFalse(session.isBattleParticipant())
        assertEquals("Leave battle", session.menuItems()[3])
    }

    @Test
    fun matchedParticipantCanForfeitBeforeIdentityPacketIsApplied() {
        val session = BattleSession()

        session.setBattleParticipant(true)
        session.setLiveBattleActive(true)

        assertTrue(session.isBattleParticipant())
        assertEquals("Forfeit", session.menuItems()[3])

        session.setSpectatorMode(true)

        assertFalse(session.isBattleParticipant())
        assertEquals("Leave battle", session.menuItems()[3])
    }

    @Test
    fun spectatorModeClearsRequestsThatArriveWhileWatching() {
        val session = BattleSession()

        session.setLiveBattleActive(true)
        session.setSpectatorMode(true)
        session.applyProtocolLine(
            "|request|{\"rqid\":1,\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35,\"maxpp\":35}]}]}"
        )

        assertFalse(session.decisionAvailable)
        assertEquals(BattleSession.DecisionKind.WAIT, session.decisionKind)
    }

    @Test
    fun spectatorModeNeverAutoPassesAForcedSwitchRequest() {
        val session = BattleSession()
        val decisions = mutableListOf<String>()
        session.addDecisionListener { decisions += it }
        session.setLiveBattleActive(true)
        session.setSpectatorMode(true)

        session.applyProtocolPacket(
            listOf("|request|{\"rqid\":2,\"forceSwitch\":[true],\"side\":{\"pokemon\":[]}}")
        )

        assertTrue(decisions.isEmpty())
        assertFalse(session.decisionAvailable)
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
