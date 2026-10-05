package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownReplayPlaybackParityTest {
    private data class ReplayCase(
        val fileName: String,
        val requiredIdentityTransitions: Set<String>
    )

    private data class ExpectedMove(
        val identity: ProtocolMoveIdentity,
        val playerSide: Boolean,
        val messageId: Long
    )

    private data class ProtocolMoveIdentity(
        val line: String,
        val actorName: String,
        val actorSlot: String,
        val actorSpecies: String
    )

    private data class ProtocolPokemonIdentity(
        val slot: String,
        val name: String,
        val species: String
    )

    @Test
    fun everyPokemonNamedByOfficialReplayFeedMatchesAVisibleSprite() {
        listOf(
            "gen1ou-2692779867.json",
            "gen2ou-2692782179.json",
            "gen3ou-2692783639.json",
            "gen8ou-2692756742.json",
            "gen9randombattle-2691947817.json",
            "gen9doublesou-2691960998.json",
            "gen9randombattle-2691989691.json",
            "gen9randombattle-2691985124.json",
            "gen9randombattle-2691982973.json"
        ).forEach { fileName ->
            val replayJson = checkNotNull(javaClass.getResourceAsStream("/showdown-replays/$fileName"))
                .bufferedReader()
                .use { it.readText() }
            val replay = ShowdownReplayImporter.payload(replayJson)
            val playerNames = replay.players.distinct()
            val knownPokemonNames = replay.log.lines().mapNotNull(::protocolPokemonName).toSet()

            playerNames.forEach { localUsername ->
                val localPlayerSlots = protocolPlayerSlots(replay.log.lines(), localUsername)
                listOf(false, true).forEach { replayMode ->
                    listOf(false, true).forEach { nativeFeedMode ->
                        val session = BattleSession().apply {
                            applyProtocolLine("|init|battle")
                            setLocalUsername(localUsername)
                            setReplayMode(replayMode)
                        }
                        val presentation = BattleFeedPresentation().apply { setPlaybackSpeed(1f) }
                        val offScreenSourcesByMessage = mutableMapOf<Long, Set<String>>()
                        val protocolCombatantsBySlot = mutableMapOf<String, ProtocolPokemonIdentity>()
                        val protocolActorsByMessage = mutableMapOf<Long, List<ProtocolPokemonIdentity>>()
                        val messageOriginPackets = mutableMapOf<Long, List<String>>()
                        val presentedProtocolActorMessages = mutableSetOf<Long>()
                        var nowMillis = 0L
                        var previousProtocolEntries = session.battleLog()

                        BattlePlaybackTiming.chunks(replay.log.lines()).forEach { packet ->
                            val previousMessageIds = session.battleFeedMessages().mapTo(mutableSetOf()) { it.id }
                            val packetOffScreenSources = packet.flatMapTo(mutableSetOf(), ::protocolOffScreenSourceNames)
                            val packetIdentities = protocolPokemonIdentitiesInPacket(packet, protocolCombatantsBySlot)
                            session.applyProtocolPacket(packet)
                            val currentProtocolEntries = session.battleLog()
                            val generatedProtocolEntries = appendedProtocolEntries(
                                previousProtocolEntries,
                                currentProtocolEntries
                            )
                            previousProtocolEntries = currentProtocolEntries
                            if (nativeFeedMode && generatedProtocolEntries.isNotEmpty()) {
                                session.appendShowdownBattleLog(
                                    showdownMarkup(generatedProtocolEntries),
                                    session.battleLogGeneration()
                                )
                            }
                            if (nativeFeedMode) session.markNativeBattleLogSynchronized(session.battleLogGeneration())
                            val messages = session.battleFeedMessages()
                            val generatedProtocolMessages = messages.filter { it.id !in previousMessageIds }
                            generatedProtocolMessages.forEach { message ->
                                messageOriginPackets.putIfAbsent(message.id, packet)
                            }
                            generatedProtocolMessages.forEach { message ->
                                val referencedActors = packetIdentities.filter { identity ->
                                    mentionsPokemon(message.text, identity.name)
                                }.distinctBy { it.slot to it.name }
                                if (referencedActors.isNotEmpty()) {
                                    protocolActorsByMessage[message.id] = referencedActors
                                }
                                val referencedSources = packetOffScreenSources.filterTo(mutableSetOf()) { name ->
                                    mentionsPokemon(message.text, name)
                                }
                                if (referencedSources.isNotEmpty()) {
                                    offScreenSourcesByMessage[message.id] = referencedSources
                                }
                            }
                            presentation.updateMessages(messages, session.battleFeedVisible, nowMillis)
                            val pauseMillis = BattlePlaybackTiming.scaledPause(
                                BattlePlaybackTiming.pauseAfter(
                                    packet,
                                    messages.count { it.id !in previousMessageIds },
                                    presentation.remainingPlaybackBudgetMillis(nowMillis)
                                ),
                                1f
                            )
                            val deadlineMillis = nowMillis + pauseMillis
                            var frameTimeMillis = nowMillis

                            while (frameTimeMillis <= deadlineMillis) {
                                presentation.frame(frameTimeMillis)?.let { frame ->
                                    val sceneSnapshot = session.battleSceneSnapshotForFeedMessage(frame.messageId)
                                    val switchOutVisual = session.switchOutVisualForBattleFeed(frame.messageId)
                                    val playerCombatants = BattleFeedSceneState.combatantsForMessage(
                                        sceneSnapshot?.playerCombatants ?: session.playerActiveCombatants(),
                                        true,
                                        switchOutVisual
                                    )
                                    val opponentCombatants = BattleFeedSceneState.combatantsForMessage(
                                        sceneSnapshot?.opponentCombatants ?: session.opponentActiveCombatants(),
                                        false,
                                        switchOutVisual
                                    )
                                    val spriteRequests = BattleSpriteRequests.forScene(
                                        playerCombatants = playerCombatants,
                                        opponentCombatants = opponentCombatants,
                                        singlesBattle = session.isSinglesBattle(),
                                        style = session.spriteStyle,
                                        playerFallbackSpecies = session.playerPokemon,
                                        opponentFallbackSpecies = session.opponentPokemon
                                    )
                                    val visibleCombatants = playerCombatants.map { BattleSpriteSide.PLAYER to it } +
                                        opponentCombatants.map { BattleSpriteSide.OPPONENT to it }

                                    val protocolActors = protocolActorsByMessage[frame.messageId].orEmpty()
                                    if (protocolActors.isNotEmpty()) {
                                        frame.messageId?.let(presentedProtocolActorMessages::add)
                                    }
                                    protocolActors.forEach { identity ->
                                        val expectedPlayerSide = identity.slot.take(2) in localPlayerSlots
                                        val matchingCombatant = visibleCombatants.singleOrNull { (side, combatant) ->
                                            combatant.slot.equals(identity.slot, ignoreCase = true) &&
                                                (side == BattleSpriteSide.PLAYER) == expectedPlayerSide
                                        }
                                        val context = "$fileName ($localUsername, replay=$replayMode, native=$nativeFeedMode): ${frame.visibleText} at $frameTimeMillis ms for ${identity.slot}:${identity.name}"
                                        assertTrue(
                                            "$context; visible combatants are ${visibleCombatants.map { it.first to it.second.name }}",
                                            matchingCombatant != null
                                        )
                                        val (side, combatant) = checkNotNull(matchingCombatant)
                                        assertEquals(context, identity.name, combatant.name)
                                        assertEquals(context, identity.species, combatant.species)
                                        val sprite = when {
                                            side == BattleSpriteSide.PLAYER && spriteRequests.singlesBattle -> spriteRequests.playerLead
                                            side == BattleSpriteSide.OPPONENT && spriteRequests.singlesBattle -> spriteRequests.opponentLead
                                            side == BattleSpriteSide.PLAYER -> spriteRequests.playerActive
                                                .singleOrNull { it.slot == identity.slot }
                                                ?.request
                                            else -> spriteRequests.opponentActive
                                                .singleOrNull { it.slot == identity.slot }
                                                ?.request
                                        }
                                        assertEquals(context, identity.species, checkNotNull(sprite).species)
                                        assertEquals(
                                            context,
                                            if (expectedPlayerSide) BattleSpriteSide.PLAYER else BattleSpriteSide.OPPONENT,
                                            checkNotNull(sprite).side
                                        )
                                    }

                                    knownPokemonNames.filter { name ->
                                        mentionsPokemon(frame.visibleText, name)
                                    }.forEach { name ->
                                        val matchingCombatants = visibleCombatants.filter { (_, combatant) ->
                                            combatant.name.equals(name, ignoreCase = true) ||
                                                combatant.species.equals(name, ignoreCase = true)
                                        }
                                        val currentFeedSummary = messages.takeLast(8).map { "${it.id}:${it.text}" }
                                        val mappedMessageIds = protocolActorsByMessage.keys.sorted().takeLast(8)
                                        val context = "$fileName ($localUsername, replay=$replayMode, native=$nativeFeedMode): ${frame.visibleText} [message ${frame.messageId}] at $frameTimeMillis ms after $packet; origin=${frame.messageId?.let(messageOriginPackets::get)}; feed=$currentFeedSummary; mapped=$mappedMessageIds"
                                        val messageIdentities = protocolActorsByMessage[frame.messageId].orEmpty()
                                        val matchingProtocolIdentity = messageIdentities.any {
                                            it.name.equals(name, ignoreCase = true) || it.species.equals(name, ignoreCase = true)
                                        }
                                        val offScreenSource = name in offScreenSourcesByMessage[frame.messageId].orEmpty()
                                        assertTrue(
                                            "$context did not retain protocol identity for $name; mapped identities are ${messageIdentities.map { it.slot to it.name }}",
                                            matchingProtocolIdentity || offScreenSource
                                        )
                                        if (matchingCombatants.isEmpty() && offScreenSource) {
                                            return@forEach
                                        }
                                        assertTrue(
                                            "$context; active combatants are ${visibleCombatants.map { it.second.name }}",
                                            matchingCombatants.isNotEmpty()
                                        )
                                        matchingCombatants.forEach { (side, combatant) ->
                                            val sprite = when {
                                                side == BattleSpriteSide.PLAYER && spriteRequests.singlesBattle ->
                                                    spriteRequests.playerLead
                                                side == BattleSpriteSide.OPPONENT && spriteRequests.singlesBattle ->
                                                    spriteRequests.opponentLead
                                                side == BattleSpriteSide.PLAYER -> spriteRequests.playerActive
                                                    .singleOrNull { it.slot == combatant.slot }
                                                    ?.request
                                                else -> spriteRequests.opponentActive
                                                    .singleOrNull { it.slot == combatant.slot }
                                                    ?.request
                                            }

                                            assertEquals(context, combatant.species, checkNotNull(sprite).species)
                                            assertEquals(context, combatant.shiny, checkNotNull(sprite).shiny)
                                        }
                                    }
                                }
                                frameTimeMillis += 100L
                            }
                            nowMillis = deadlineMillis
                        }

                        assertTrue(
                            "$fileName as $localUsername replay=$replayMode native=$nativeFeedMode mapped too few protocol identities to user-facing log messages",
                            protocolActorsByMessage.size >= 20
                        )
                        assertEquals(
                            "$fileName as $localUsername replay=$replayMode native=$nativeFeedMode left identity-bearing log messages unpresented",
                            protocolActorsByMessage.keys,
                            presentedProtocolActorMessages
                        )
                    }
                }
            }
        }
    }

    @Test
    fun officialReplayMoveActorsStayAlignedWithTheVisibleSprites() {
        listOf(
            ReplayCase("gen1ou-2692779867.json", emptySet()),
            ReplayCase("gen2ou-2692782179.json", emptySet()),
            ReplayCase("gen3ou-2692783639.json", emptySet()),
            ReplayCase("gen8ou-2692756742.json", setOf("-formechange")),
            ReplayCase("gen9randombattle-2691947817.json", emptySet()),
            ReplayCase("gen9doublesou-2691960998.json", emptySet()),
            ReplayCase("gen9randombattle-2691989691.json", setOf("-formechange", "-transform")),
            ReplayCase("gen9randombattle-2691985124.json", setOf("-formechange")),
            ReplayCase("gen9randombattle-2691982973.json", setOf("replace"))
        ).forEach { replayCase ->
            val replayJson = checkNotNull(javaClass.getResourceAsStream("/showdown-replays/${replayCase.fileName}"))
                .bufferedReader()
                .use { it.readText() }
            val replayPlayers = ShowdownReplayImporter.payload(replayJson).players.distinct()
            assertEquals(replayCase.fileName, 2, replayPlayers.size)
            replayPlayers.forEach { localUsername ->
                listOf(false, true).forEach { replayMode ->
                    listOf(0.5f, 0.75f, 1f, 1.5f, 2f).forEach { speed ->
                        assertReplayMoveActorsMatchSprites(replayCase, speed, localUsername, replayMode)
                    }
                }
            }
        }
    }

    @Test
    fun multiBattleMoveActorsStayAlignedWithSpritesForEveryPlayerPerspective() {
        val transcript = listOf(
            "|player|p1|RED||",
            "|player|p2|BLUE||",
            "|player|p3|GREEN||",
            "|player|p4|GOLD||",
            "|gametype|multi",
            "|switch|p1a: Wing|Moltres, L50|100/100",
            "|switch|p2a: Spark|Magmar, L50|100/100",
            "|switch|p3b: Cinder|Skeledirge, L50|100/100",
            "|switch|p4b: Shell|Drednaw, L50|100/100",
            "|move|p1a: Wing|Flamethrower|p2a: Spark",
            "|-damage|p2a: Spark|75/100",
            "|move|p2a: Spark|Fire Blast|p3b: Cinder",
            "|-damage|p3b: Cinder|70/100",
            "|move|p3b: Cinder|Torch Song|p4b: Shell",
            "|-damage|p4b: Shell|65/100",
            "|move|p4b: Shell|Liquidation|p1a: Wing",
            "|-damage|p1a: Wing|80/100"
        )
        listOf("RED", "BLUE", "GREEN", "GOLD").forEach { localUsername ->
            listOf(false, true).forEach { replayMode ->
                listOf(0.75f, 1.5f).forEach { speed ->
                    assertMoveActorsMatchSprites(
                        transcript,
                        "anonymized-multi-replay",
                        emptySet(),
                        speed,
                        localUsername,
                        replayMode,
                        minimumMoveCount = 4
                    )
                }
            }
        }
    }

    @Test
    fun replayKeepsTheOriginalFormVisibleUntilItsMoveMessageHasPlayed() {
        val replayJson = checkNotNull(javaClass.getResourceAsStream("/showdown-replays/gen9randombattle-2691985124.json"))
            .bufferedReader()
            .use { it.readText() }
        val replay = ShowdownReplayImporter.payload(replayJson)
        val chunks = BattlePlaybackTiming.chunks(replay.log.lines())
        val moveChunkIndex = chunks.indexOfFirst { chunk ->
            chunk.any { it == "|move|p1a: Cramorant|Surf|p2a: Perrserker" }
        }
        val formChangeChunkIndex = chunks.indexOfFirst { chunk ->
            chunk.any { it.startsWith("|-formechange|p1a: Cramorant|Cramorant-Gulping|") }
        }
        assertTrue("replay fixture has no Cramorant Surf", moveChunkIndex >= 0)
        assertTrue("Cramorant's form change was not isolated after its Surf message", formChangeChunkIndex > moveChunkIndex)

        val session = BattleSession().apply {
            setLocalUsername(replay.players.firstOrNull().orEmpty())
            setReplayMode(true)
        }
        chunks.take(moveChunkIndex + 1).forEach(session::applyProtocolPacket)

        assertTrue(session.battleFeedMessages().any { it.text.contains("Cramorant used Surf!") })
        val moveSprite = BattleSpriteRequests.active(
            session.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            session.spriteStyle
        ).single().request
        assertEquals("Cramorant", moveSprite.species)

        session.applyProtocolPacket(chunks[formChangeChunkIndex])

        val changedSprite = BattleSpriteRequests.active(
            session.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            session.spriteStyle
        ).single().request
        assertEquals("Cramorant-Gulping", changedSprite.species)
    }

    @Test
    fun replayDamageNarrationStaysWithThePokemonShownUntilThatMessageClears() {
        val replayJson = checkNotNull(javaClass.getResourceAsStream("/showdown-replays/gen9randombattle-2691985124.json"))
            .bufferedReader()
            .use { it.readText() }
        val replay = ShowdownReplayImporter.payload(replayJson)
        val chunks = BattlePlaybackTiming.chunks(replay.log.lines())
        val lugiaSwitchIndex = chunks.indexOfFirst { chunk ->
            chunk.any { it.startsWith("|switch|p2a: Lugia|") }
        }
        val lugiaDamageIndex = chunks.indexOfFirst { chunk ->
            chunk.any { it.startsWith("|-damage|p2a: Lugia|") }
        }
        assertTrue("replay fixture has no Perrserker to Lugia switch", lugiaSwitchIndex >= 0)
        assertTrue("replay fixture has no damage to Lugia after the switch", lugiaDamageIndex > lugiaSwitchIndex)

        val session = BattleSession().apply {
            setLocalUsername(replay.players.firstOrNull().orEmpty())
            setReplayMode(true)
        }
        val presentation = BattleFeedPresentation().apply { setPlaybackSpeed(0.75f) }
        var nowMillis = 0L
        var sawPerrserkerDamageMessage = false
        var sawLugiaSendoutMessage = false

        chunks.take(lugiaDamageIndex + 1).forEach { packet ->
            val previousMessageIds = session.battleFeedMessages().mapTo(mutableSetOf()) { it.id }
            session.applyProtocolPacket(packet)
            val messages = session.battleFeedMessages()
            val generatedMessageCount = messages.count { it.id !in previousMessageIds }
            presentation.updateMessages(messages, session.battleFeedVisible, nowMillis)
            val pauseMillis = BattlePlaybackTiming.scaledPause(
                BattlePlaybackTiming.pauseAfter(
                    packet,
                    generatedMessageCount,
                    presentation.remainingPlaybackBudgetMillis(nowMillis)
                ),
                0.75f
            )
            var frameTimeMillis = nowMillis
            val deadlineMillis = nowMillis + pauseMillis

            while (frameTimeMillis <= deadlineMillis) {
                presentation.frame(frameTimeMillis)?.let { frame ->
                    if (
                        frame.visibleText.contains("Perrserker", true) &&
                        (frame.visibleText.contains("lost", true) || frame.visibleText.contains("fell", true))
                    ) {
                        sawPerrserkerDamageMessage = true
                    }
                    if (frame.visibleText.contains("sent out Lugia", true)) sawLugiaSendoutMessage = true
                    val mentionedOpponent = listOf("Perrserker", "Lugia").firstOrNull {
                        frame.visibleText.contains(it, ignoreCase = true)
                    }
                    if (mentionedOpponent != null) {
                        val switchOutVisual = session.switchOutVisualForBattleFeed(frame.messageId)
                        val visiblePlayerCombatants = BattleFeedSceneState.combatantsForMessage(
                            session.playerActiveCombatants(),
                            true,
                            switchOutVisual
                        )
                        val visibleOpponents = BattleFeedSceneState.combatantsForMessage(
                            session.opponentActiveCombatants(),
                            false,
                            switchOutVisual
                        )
                        val visibleOpponent = visibleOpponents.firstOrNull {
                            it.name.equals(mentionedOpponent, true)
                        }
                        assertTrue(
                            "${frame.visibleText} was shown with ${visibleOpponents.map { it.name }} active at $frameTimeMillis ms",
                            visibleOpponent != null
                        )
                        val sprites = BattleSpriteRequests.forScene(
                            playerCombatants = visiblePlayerCombatants,
                            opponentCombatants = visibleOpponents,
                            singlesBattle = session.isSinglesBattle(),
                            style = session.spriteStyle,
                            playerFallbackSpecies = session.playerPokemon,
                            opponentFallbackSpecies = session.opponentPokemon
                        )
                        assertEquals(
                            "${frame.visibleText} sprite at $frameTimeMillis ms",
                            checkNotNull(visibleOpponent).species,
                            checkNotNull(sprites.opponentLead).species
                        )
                        assertEquals(BattleSpriteSide.OPPONENT, sprites.opponentLead?.side)
                    }
                }
                frameTimeMillis += 100L
            }
            nowMillis = deadlineMillis
        }

        assertTrue("replay never presented Perrserker's damage narration", sawPerrserkerDamageMessage)
        assertTrue("replay never presented Lugia's switch-in narration", sawLugiaSendoutMessage)
    }

    @Test
    fun delayedDamageNarrationKeepsItsOriginalPokemonVisibleAfterTheNextSwitch() {
        val session = BattleSession().apply {
            setLocalUsername("T0RcH3D")
            setReplayMode(true)
        }
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|T0RcH3D||",
                "|player|p2|iluvgermany||",
                "|gametype|singles",
                "|switch|p1a: Cramorant|Cramorant, L86, M|261/261",
                "|switch|p2a: Perrserker|Perrserker, L89, M|269/269"
            )
        )
        val presentation = BattleFeedPresentation()
        presentation.updateMessages(session.battleFeedMessages(), true, 0L)
        session.applyProtocolPacket(
            listOf("|-damage|p2a: Perrserker|155/269|[from] move: Surf|[of] p1a: Cramorant")
        )
        val firstDamageMessage = session.battleFeedMessages().last()
        presentation.updateMessages(session.battleFeedMessages(), true, 100L)
        session.applyProtocolPacket(
            listOf("|-damage|p2a: Perrserker|88/269|[from] ability: Gulp Missile|[of] p1a: Cramorant")
        )
        presentation.updateMessages(session.battleFeedMessages(), true, 200L)
        val damageMessage = session.battleFeedMessages().last { it.text.contains("Perrserker lost 25%", true) }
        session.applyProtocolPacket(listOf("|switch|p2a: Lugia|Lugia, L73|275/275"))
        presentation.updateMessages(session.battleFeedMessages(), true, 300L)
        session.appendShowdownBattleLog(
            "The opposing Perrserker lost 42% of its health!\nThe opposing Perrserker lost some of its HP!",
            session.battleLogGeneration()
        )
        session.markNativeBattleLogSynchronized(session.battleLogGeneration())

        val frame = checkNotNull(presentation.frame(4_900L))
        assertTrue(frame.visibleText.contains("Perrserker", true))
        assertEquals("Lugia", session.opponentActiveCombatants().single().name)
        assertEquals(
            "${damageMessage.text} -> ${session.showdownBattleLog()}",
            listOf(firstDamageMessage.id, damageMessage.id),
            session.battleFeedMessages().map { it.id }
        )
        val switchOutVisual = session.switchOutVisualForBattleFeed(frame.messageId)
        val sceneSnapshot = checkNotNull(session.battleSceneSnapshotForFeedMessage(frame.messageId))
        val visiblePlayerCombatants = BattleFeedSceneState.combatantsForMessage(
            sceneSnapshot.playerCombatants,
            true,
            switchOutVisual
        )
        val visibleOpponents = BattleFeedSceneState.combatantsForMessage(
            sceneSnapshot.opponentCombatants,
            false,
            switchOutVisual
        )
        assertEquals("Perrserker", visibleOpponents.single().name)
        val sprites = BattleSpriteRequests.forScene(
            playerCombatants = visiblePlayerCombatants,
            opponentCombatants = visibleOpponents,
            singlesBattle = true,
            style = session.spriteStyle,
            playerFallbackSpecies = session.playerPokemon,
            opponentFallbackSpecies = session.opponentPokemon
        )
        assertEquals("Perrserker", sprites.opponentLead?.species)
    }

    @Test
    fun replayUpdatesAegislashFormAndSpriteTogether() {
        val replayJson = checkNotNull(javaClass.getResourceAsStream("/showdown-replays/gen8ou-2692756742.json"))
            .bufferedReader()
            .use { it.readText() }
        val replay = ShowdownReplayImporter.payload(replayJson)
        val chunks = BattlePlaybackTiming.chunks(replay.log.lines())
        val formChangeChunkIndex = chunks.indexOfFirst { chunk ->
            chunk.any { it.startsWith("|-formechange|p1a: Aegislash|Aegislash-Blade|") }
        }
        assertTrue("replay fixture has no Aegislash Blade form change", formChangeChunkIndex >= 0)

        val session = BattleSession().apply {
            setLocalUsername(replay.players.firstOrNull().orEmpty())
            setReplayMode(true)
        }
        chunks.take(formChangeChunkIndex).forEach(session::applyProtocolPacket)

        val originalForm = session.playerActiveCombatants().single { it.slot == "p1a" }
        assertEquals("Aegislash", originalForm.species)

        session.applyProtocolPacket(chunks[formChangeChunkIndex])

        val changedForm = session.playerActiveCombatants().single { it.slot == "p1a" }
        assertEquals("Aegislash-Blade", changedForm.species)
        val changedSprite = BattleSpriteRequests.active(
            session.playerActiveCombatants(),
            BattleSpriteSide.PLAYER,
            session.spriteStyle
        ).single { it.slot == "p1a" }.request
        assertEquals("Aegislash-Blade", changedSprite.species)
    }

    @Test
    fun permanentFormChangesKeepReplayMoveActorsAlignedWithProtocolSpecies() {
        assertMoveActorsMatchSprites(
            lines = listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|gametype|singles",
                "|switch|p1a: Crown|Zacian, L50|100/100",
                "|switch|p2a: Rival|Eternatus, L50|100/100",
                "|detailschange|p1a: Crown|Zacian-Crowned, L50|100/100",
                "|move|p1a: Crown|Behemoth Blade|p2a: Rival",
                "|-damage|p2a: Rival|75/100"
            ),
            replayId = "permanent-form-change",
            requiredIdentityTransitions = setOf("detailschange"),
            speed = 1f,
            localUsername = "RED",
            replayMode = true,
            minimumMoveCount = 1
        )
    }

    @Test
    fun megaAndPrimalMovesKeepTheirProtocolFormsDuringReplayDwell() {
        listOf(
            listOf(
                "|switch|p1a: Crown|Mawile, L50|100/100",
                "|detailschange|p1a: Crown|Mawile-Mega, L50|100/100",
                "|-mega|p1a: Crown|Mawile|Mawilite",
                "|move|p1a: Crown|Play Rough|p2a: Rival"
            ) to setOf("detailschange", "-mega"),
            listOf(
                "|switch|p1a: Terra|Groudon, L50|100/100",
                "|detailschange|p1a: Terra|Groudon-Primal, L50|100/100",
                "|-primal|p1a: Terra",
                "|move|p1a: Terra|Precipice Blades|p2a: Rival"
            ) to setOf("detailschange", "-primal")
        ).forEachIndexed { index, (transitionLines, requiredTransitions) ->
            assertMoveActorsMatchSprites(
                lines = listOf(
                    "|player|p1|RED||",
                    "|player|p2|BLUE||",
                    "|gametype|singles",
                    *transitionLines.take(1).toTypedArray(),
                    "|switch|p2a: Rival|Eternatus, L50|100/100",
                    *transitionLines.drop(1).toTypedArray(),
                    "|-damage|p2a: Rival|75/100"
                ),
                replayId = "permanent-gimmick-form-$index",
                requiredIdentityTransitions = requiredTransitions,
                speed = 1f,
                localUsername = "RED",
                replayMode = true,
                minimumMoveCount = 1
            )
        }
    }

    @Test
    fun ultraBurstKeepsThePermanentFormForFollowingReplayMoves() {
        assertMoveActorsMatchSprites(
            lines = listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|gametype|singles",
                "|switch|p1a: Necrozma|Necrozma, L50|100/100",
                "|switch|p2a: Rival|Eternatus, L50|100/100",
                "|detailschange|p1a: Necrozma|Necrozma-Ultra, L50|100/100",
                "|-burst|p1a: Necrozma|Necrozma|Ultranecrozium Z",
                "|move|p1a: Necrozma|Photon Geyser|p2a: Rival",
                "|-damage|p2a: Rival|75/100"
            ),
            replayId = "ultra-burst-form-change",
            requiredIdentityTransitions = setOf("detailschange", "-burst"),
            speed = 1f,
            localUsername = "RED",
            replayMode = true,
            minimumMoveCount = 1
        )
    }

    @Test
    fun doubleBattleMoveFrameUsesTheSlotLayoutFromBeforeItsFollowingSwap() {
        val chunks = BattlePlaybackTiming.chunks(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|gametype|doubles",
                "|switch|p1a: Sparky|Pikachu, L50|100/100",
                "|switch|p1b: Wrench|Rotom-Wash, L50|100/100",
                "|switch|p2a: Rival|Eevee, L50|100/100",
                "|move|p1a: Sparky|Ally Switch|p1b: Wrench",
                "|swap|p1a: Sparky|1"
            )
        )
        val moveChunkIndex = chunks.indexOfFirst { chunk -> chunk.any { it.startsWith("|move|") } }
        val swapChunkIndex = chunks.indexOfFirst { chunk -> chunk.any { it.startsWith("|swap|") } }
        assertTrue("move and following slot swap were not isolated", swapChunkIndex > moveChunkIndex)

        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        chunks.take(moveChunkIndex + 1).forEach(session::applyProtocolPacket)
        val beforeSwap = session.playerActiveCombatants().associate { it.slot to it.name }
        assertEquals("Sparky", beforeSwap["p1a"])
        assertEquals("Wrench", beforeSwap["p1b"])

        session.applyProtocolPacket(chunks[swapChunkIndex])

        val afterSwap = session.playerActiveCombatants().associate { it.slot to it.name }
        assertEquals("Wrench", afterSwap["p1a"])
        assertEquals("Sparky", afterSwap["p1b"])
    }

    @Test
    fun repeatedTripleBattleSlotSwapsKeepMoveActorsAndSpritesAligned() {
        val chunks = BattlePlaybackTiming.chunks(
            listOf(
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|gametype|triples",
                "|switch|p1a: Spark|Pikachu, L50|100/100",
                "|switch|p1b: Tide|Gyarados, L50|100/100",
                "|switch|p1c: Leaf|Meowscarada, L50|100/100",
                "|switch|p2a: Atlas|Dragonite, L50|100/100",
                "|switch|p2b: Volt|Zapdos, L50|100/100",
                "|switch|p2c: Stone|Garchomp, L50|100/100",
                "|move|p1a: Spark|Thunderbolt|p2a: Atlas",
                "|move|p2c: Stone|Earthquake|p1c: Leaf",
                "|swap|p1a: Spark|1",
                "|move|p1b: Spark|Thunderbolt|p2c: Stone",
                "|swap|p1c: Leaf|1",
                "|move|p1c: Spark|Thunderbolt|p2b: Volt",
                "|swap|p1a: Tide|1",
                "|move|p1b: Tide|Waterfall|p2a: Atlas",
                "|swap|p1c: Spark|1",
                "|move|p1b: Spark|Thunderbolt|p2c: Stone",
                "|swap|p2c: Stone|1",
                "|move|p2b: Stone|Earthquake|p1a: Leaf",
                "|swap|p2a: Atlas|1",
                "|move|p2b: Atlas|Dragon Claw|p1b: Spark",
                "|swap|p2c: Volt|1",
                "|move|p2c: Atlas|Dragon Claw|p1c: Tide"
            )
        )
        assertMoveActorsMatchSprites(
            lines = chunks.flatten(),
            replayId = "anonymized-triple-replay",
            requiredIdentityTransitions = setOf("swap"),
            speed = 1f,
            localUsername = "RED",
            replayMode = true,
            minimumMoveCount = 6
        )
    }

    private fun assertReplayMoveActorsMatchSprites(
        replayCase: ReplayCase,
        speed: Float,
        localUsername: String,
        replayMode: Boolean
    ) {
        val replayJson = checkNotNull(
            javaClass.getResourceAsStream("/showdown-replays/${replayCase.fileName}")
        ).bufferedReader().use { it.readText() }
        val replay = ShowdownReplayImporter.payload(replayJson)
        val observedIdentityTransitions = replay.log.lines().mapNotNull { line ->
            line.split('|').getOrNull(1)?.takeIf { it in replayCase.requiredIdentityTransitions }
        }.toSet()
        assertEquals(replayCase.fileName, replayCase.requiredIdentityTransitions, observedIdentityTransitions)
        assertMoveActorsMatchSprites(
            replay.log.lines(),
            replay.id,
            replayCase.requiredIdentityTransitions,
            speed,
            localUsername,
            replayMode,
            minimumMoveCount = 10
        )
    }

    private fun assertMoveActorsMatchSprites(
        lines: List<String>,
        replayId: String,
        requiredIdentityTransitions: Set<String>,
        speed: Float,
        localUsername: String,
        replayMode: Boolean,
        minimumMoveCount: Int
    ) {
        val observedIdentityTransitions = lines.mapNotNull { line ->
            line.split('|').getOrNull(1)?.takeIf { it in requiredIdentityTransitions }
        }.toSet()
        assertEquals(replayId, requiredIdentityTransitions, observedIdentityTransitions)
        val session = BattleSession().apply {
            setLocalUsername(localUsername)
            setReplayMode(replayMode)
        }
        val presentation = BattleFeedPresentation().apply { setPlaybackSpeed(speed) }
        val expectedMoves = linkedMapOf<Long, ExpectedMove>()
        val expectedSpeciesBySlot = mutableMapOf<String, String>()
        val expectedPlayerSlot = lines.firstNotNullOfOrNull { line ->
            val fields = line.split('|')
            if (fields.getOrNull(1) == "player" && fields.getOrNull(3)?.equals(localUsername, true) == true) {
                fields.getOrNull(2)
            } else {
                null
            }
        } ?: "p1"
        val expectedPlayerSlots = if (lines.any { it == "|gametype|multi" }) {
            if (expectedPlayerSlot == "p1" || expectedPlayerSlot == "p3") setOf("p1", "p3") else setOf("p2", "p4")
        } else {
            setOf(expectedPlayerSlot)
        }
        var nowMillis = 0L
        var moveCount = 0

        BattlePlaybackTiming.chunks(lines).forEach { packet ->
            val protocolMoves = protocolMovesInPacket(packet, expectedSpeciesBySlot, replayId)
            val previousMessageIds = session.battleFeedMessages().mapTo(mutableSetOf()) { it.id }
            session.applyProtocolPacket(packet)
            val messages = session.battleFeedMessages()
            val generatedMessages = messages.filter { it.id !in previousMessageIds }
            presentation.updateMessages(messages, session.battleFeedVisible, nowMillis)
            val packetMoveMessageIds = mutableListOf<Long>()
            val observedPacketMoveMessages = mutableSetOf<Long>()
            val pauseMillis = BattlePlaybackTiming.scaledPause(
                BattlePlaybackTiming.pauseAfter(
                    packet,
                    generatedMessages.size,
                    presentation.remainingPlaybackBudgetMillis(nowMillis)
                ),
                speed
            )

            val remainingMoveMessages = generatedMessages.filter { it.text.contains(" used ") }.toMutableList()
            protocolMoves.forEach { protocolMove ->
                val context = "$replayId at ${protocolMove.line}"
                val actorSlot = protocolMove.actorSlot
                val playerSide = actorSlot.dropLast(1) in expectedPlayerSlots
                val messageIndex = remainingMoveMessages.indexOfFirst { message ->
                    messageActor(message.text).equals(protocolMove.actorName, true)
                }
                assertTrue("$context produced no move feed message", messageIndex >= 0)
                val expectedMessage = remainingMoveMessages.removeAt(messageIndex)
                val activeCombatants = if (playerSide) {
                    session.playerActiveCombatants()
                } else {
                    session.opponentActiveCombatants()
                }
                val activeActor = activeCombatants.singleOrNull { it.slot == actorSlot }
                assertTrue("$context has no active combatant for its actor", activeActor != null)
                val observedActor = checkNotNull(activeActor)
                assertEquals(context, protocolMove.actorName.lowercase(), observedActor.name.lowercase())
                assertEquals(context, protocolMove.actorSpecies, observedActor.species)
                assertEquals(context, protocolMove.actorName.lowercase(), messageActor(expectedMessage.text).lowercase())

                expectedMoves[expectedMessage.id] = ExpectedMove(
                    protocolMove,
                    playerSide,
                    expectedMessage.id
                )
                packetMoveMessageIds += expectedMessage.id
                moveCount += 1
            }

            var frameTimeMillis = nowMillis
            val deadlineMillis = nowMillis + pauseMillis
            while (frameTimeMillis <= deadlineMillis) {
                val frame = presentation.frame(frameTimeMillis)
                if (frame != null) {
                    val expectedMove = frame.messageId?.let(expectedMoves::get)
                    if (expectedMove != null) {
                        val context = "$replayId at ${expectedMove.identity.line}"
                        observedPacketMoveMessages += expectedMove.messageId
                        assertEquals(context, expectedMove.identity.actorName.lowercase(), messageActor(frame.visibleText).lowercase())
                        val playerCombatants = BattleFeedSceneState.combatantsForMessage(
                            session.playerActiveCombatants(),
                            true,
                            session.switchOutVisualForBattleFeed(frame.messageId)
                        )
                        val opponentCombatants = BattleFeedSceneState.combatantsForMessage(
                            session.opponentActiveCombatants(),
                            false,
                            session.switchOutVisualForBattleFeed(frame.messageId)
                        )
                        val visibleCombatants = if (expectedMove.playerSide) playerCombatants else opponentCombatants
                        val visibleActor = visibleCombatants.singleOrNull { it.slot == expectedMove.identity.actorSlot }
                        val frameContext = "$context was rendered at simulated time $frameTimeMillis for $packet with message ${frame.messageId}"
                        assertTrue(
                            "$frameContext while active Pokémon are ${visibleCombatants.map { it.name }}",
                            visibleActor != null
                        )
                        val activeActor = checkNotNull(visibleActor)
                        assertEquals(frameContext, expectedMove.identity.actorName.lowercase(), activeActor.name.lowercase())
                        assertEquals(frameContext, expectedMove.identity.actorSpecies, activeActor.species)
                        val spriteRequests = BattleSpriteRequests.forScene(
                            playerCombatants = playerCombatants,
                            opponentCombatants = opponentCombatants,
                            singlesBattle = session.isSinglesBattle(),
                            style = session.spriteStyle,
                            playerFallbackSpecies = session.playerPokemon,
                            opponentFallbackSpecies = session.opponentPokemon
                        )
                        val renderedSprite = when {
                            expectedMove.playerSide && spriteRequests.singlesBattle -> spriteRequests.playerLead
                            !expectedMove.playerSide && spriteRequests.singlesBattle -> spriteRequests.opponentLead
                            expectedMove.playerSide -> spriteRequests.playerActive
                                .singleOrNull { it.slot == expectedMove.identity.actorSlot }
                                ?.request
                            else -> spriteRequests.opponentActive
                                .singleOrNull { it.slot == expectedMove.identity.actorSlot }
                                ?.request
                        }
                        assertEquals(frameContext, expectedMove.identity.actorSpecies, checkNotNull(renderedSprite).species)
                        assertEquals(frameContext, activeActor.shiny, checkNotNull(renderedSprite).shiny)
                        assertEquals(
                            frameContext,
                            if (expectedMove.playerSide) BattleSpriteSide.PLAYER else BattleSpriteSide.OPPONENT,
                            checkNotNull(renderedSprite).side
                        )
                    }
                }
                frameTimeMillis += 100L
            }
            packetMoveMessageIds.forEach { messageId ->
                val expectedMove = checkNotNull(expectedMoves[messageId])
                assertTrue(
                    "$replayId at ${expectedMove.identity.line} was not shown during its action dwell",
                    messageId in observedPacketMoveMessages
                )
            }
            nowMillis += pauseMillis
        }

        assertTrue("$replayId did not exercise enough move events", moveCount >= minimumMoveCount)
    }

    private fun protocolMovesInPacket(
        packet: List<String>,
        speciesBySlot: MutableMap<String, String>,
        replayId: String
    ): List<ProtocolMoveIdentity> = buildList {
        packet.forEach { line ->
            val fields = line.split('|')
            val action = fields.getOrNull(1)
            val actorSlot = fields.getOrNull(2)?.substringBefore(':')?.trim().orEmpty()
            when (action) {
                "switch", "drag", "replace" -> fields.getOrNull(3)
                    ?.substringBefore(',')
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let { speciesBySlot[actorSlot] = it }
                "detailschange", "-formechange" -> fields.getOrNull(3)
                    ?.takeIf(String::isNotBlank)
                    ?.substringBefore(',')
                    ?.trim()
                    ?.let { speciesBySlot[actorSlot] = it }
                "-transform" -> {
                    val targetSlot = fields.getOrNull(3)?.substringBefore(':')?.trim().orEmpty()
                    speciesBySlot[targetSlot]?.let { speciesBySlot[actorSlot] = it }
                }
                "swap" -> {
                    val target = fields.getOrNull(3).orEmpty()
                    val targetSlot = target.toIntOrNull()?.let { position ->
                        actorSlot.dropLast(1) + ('a'.code + position).toChar()
                    } ?: target.substringBefore(':').trim()
                    if (targetSlot.isNotBlank() && actorSlot.isNotBlank()) {
                        val movingSpecies = speciesBySlot.remove(actorSlot)
                        val displacedSpecies = speciesBySlot.remove(targetSlot)
                        movingSpecies?.let { speciesBySlot[targetSlot] = it }
                        displacedSpecies?.let { speciesBySlot[actorSlot] = it }
                    }
                }
                "move" -> {
                    val species = speciesBySlot[actorSlot]
                    assertTrue("$replayId at $line has no prior protocol species for $actorSlot", species != null)
                    val actorIdent = fields.getOrElse(2) { "" }
                    add(
                        ProtocolMoveIdentity(
                            line = line,
                            actorName = actorIdent.substringAfter(": ", actorIdent).substringBefore(',').trim(),
                            actorSlot = actorSlot,
                            actorSpecies = checkNotNull(species)
                        )
                    )
                }
            }
        }
    }

    private fun messageActor(message: String): String = message
        .substringBefore(" used ")
        .substringAfterLast("'s ")
        .replaceFirst(Regex("^the opposing ", RegexOption.IGNORE_CASE), "")
        .trim()

    private fun protocolPokemonName(line: String): String? = line.split('|')
        .drop(2)
        .firstNotNullOfOrNull { field ->
            Regex("^p[1-4][a-z]:\\s*(.+)$", RegexOption.IGNORE_CASE)
                .matchEntire(field)
                ?.groupValues
                ?.get(1)
                ?.substringBefore(',')
                ?.trim()
                ?.takeIf { it.length >= 3 }
        }

    private fun protocolPlayerSlots(lines: List<String>, localUsername: String): Set<String> {
        val playerLines = lines.mapNotNull { line ->
            val fields = line.split('|')
            if (fields.getOrNull(1) == "player") {
                fields.getOrNull(2)?.let { it to fields.getOrNull(3).orEmpty() }
            } else {
                null
            }
        }
        val localSlots = playerLines.filter { (_, username) -> username.equals(localUsername, true) }
            .mapTo(mutableSetOf()) { (slot, _) -> slot }
        return if (lines.any { it == "|gametype|multi" }) {
            if (localSlots.any { it == "p1" || it == "p3" }) setOf("p1", "p3") else setOf("p2", "p4")
        } else {
            localSlots
        }
    }

    private fun protocolPokemonIdentitiesInPacket(
        packet: List<String>,
        combatantsBySlot: MutableMap<String, ProtocolPokemonIdentity>
    ): List<ProtocolPokemonIdentity> = buildList {
        packet.forEach { line ->
            val fields = line.split('|')
            val action = fields.getOrNull(1).orEmpty()
            val actor = fields.getOrNull(2)?.let(::protocolActivePokemonIdentity)

            when (action) {
                "switch", "drag", "replace" -> actor?.let { active ->
                    if (action == "switch") combatantsBySlot[active.slot]?.let(::add)
                    fields.getOrNull(3)
                        ?.substringBefore(',')
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { species -> combatantsBySlot[active.slot] = active.copy(species = species) }
                }
                "detailschange", "-formechange" -> actor?.let { active ->
                    fields.getOrNull(3)
                        ?.substringBefore(',')
                        ?.trim()
                        ?.takeIf(String::isNotBlank)
                        ?.let { species -> combatantsBySlot[active.slot] = active.copy(species = species) }
                }
                "-transform" -> actor?.let { active ->
                    val targetSlot = fields.getOrNull(3)?.let(::protocolActivePokemonIdentity)?.slot
                    val targetSpecies = targetSlot?.let(combatantsBySlot::get)?.species
                    combatantsBySlot[active.slot] = active.copy(species = targetSpecies ?: active.name)
                }
                "swap" -> actor?.let { active ->
                    val targetSlot = fields.getOrNull(3)?.toIntOrNull()?.let { position ->
                        active.slot.dropLast(1) + ('a'.code + position).toChar()
                    } ?: fields.getOrNull(3)?.let(::protocolActivePokemonIdentity)?.slot
                    if (targetSlot != null) {
                        val movingCombatant = combatantsBySlot[active.slot]
                        val displacedCombatant = combatantsBySlot[targetSlot]
                        displacedCombatant?.let { combatantsBySlot[active.slot] = it }
                            ?: combatantsBySlot.remove(active.slot)
                        movingCombatant?.let { combatantsBySlot[targetSlot] = it }
                            ?: combatantsBySlot.remove(targetSlot)
                    }
                }
            }

            fields.drop(2).mapNotNull(::protocolActivePokemonIdentity).forEach { active ->
                val identity = active.copy(species = combatantsBySlot[active.slot]?.species ?: active.name)
                combatantsBySlot[active.slot] = identity
                add(identity)
            }
        }
    }

    private fun protocolActivePokemonIdentity(field: String): ProtocolPokemonIdentity? {
        val match = Regex("^(p[1-4][a-z]):\\s*(.+)$", RegexOption.IGNORE_CASE).matchEntire(field) ?: return null
        val slot = match.groupValues[1]
        val name = match.groupValues[2].substringBefore(',').trim()
        return name.takeIf(String::isNotBlank)?.let { ProtocolPokemonIdentity(slot, it, it) }
    }

    private fun protocolOffScreenSourceNames(line: String): Set<String> = line.split('|')
        .drop(2)
        .mapNotNull { field ->
            Regex("^\\[(?:wisher|of)]\\s+(.+)$", RegexOption.IGNORE_CASE)
                .matchEntire(field)
                ?.groupValues
                ?.get(1)
                ?.let { source ->
                    Regex("^p[1-4][a-z]:\\s*(.+)$", RegexOption.IGNORE_CASE)
                        .matchEntire(source)
                        ?.groupValues
                        ?.get(1)
                        ?: source
                }
                ?.substringBefore(',')
                ?.trim()
                ?.takeIf { it.length >= 3 }
        }
        .toSet()

    private fun mentionsPokemon(message: String, name: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            .containsMatchIn(message)

    private fun appendedProtocolEntries(previous: List<String>, current: List<String>): List<String> {
        val overlap = (minOf(previous.size, current.size) downTo 0).first { size ->
            size == 0 || previous.takeLast(size) == current.take(size)
        }
        return current.drop(overlap)
    }

    private fun showdownMarkup(messages: List<String>) = messages.joinToString("<br />") { message ->
        message.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
    }
}
