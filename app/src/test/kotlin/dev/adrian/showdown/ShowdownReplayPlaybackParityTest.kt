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
        val line: String,
        val actorName: String,
        val actorSlot: String,
        val actorSpecies: String,
        val playerSide: Boolean,
        val messageId: Long
    )

    private data class ProtocolMoveIdentity(
        val line: String,
        val actorName: String,
        val actorSlot: String,
        val actorSpecies: String
    )

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
        val expectedSpecies = mapOf(
            "Spark" to "Pikachu",
            "Tide" to "Gyarados",
            "Leaf" to "Meowscarada",
            "Atlas" to "Dragonite",
            "Volt" to "Zapdos",
            "Stone" to "Garchomp"
        )
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        var moveCount = 0

        chunks.forEach { packet ->
            session.applyProtocolPacket(packet)
            packet.filter { it.startsWith("|move|") }.forEach { line ->
                val actorIdent = line.split('|').getOrElse(2) { "" }
                val actorSlot = actorIdent.substringBefore(':').trim()
                val actorName = actorIdent.substringAfter(": ", actorIdent).substringBefore(',').trim()
                val playerSide = actorSlot.startsWith(session.battlePlayerSlot())
                val combatants = if (playerSide) {
                    session.playerActiveCombatants()
                } else {
                    session.opponentActiveCombatants()
                }
                val context = "after triple-slot swaps at $line"
                val activeActor = combatants.singleOrNull { it.slot == actorSlot }

                assertTrue("$context has no active actor in ${combatants.map { it.slot to it.name }}", activeActor != null)
                assertEquals(context, actorName, checkNotNull(activeActor).name)
                assertEquals(context, expectedSpecies[actorName], activeActor.species)

                val spriteRequest = BattleSpriteRequests.active(
                    combatants,
                    if (playerSide) BattleSpriteSide.PLAYER else BattleSpriteSide.OPPONENT,
                    session.spriteStyle
                ).singleOrNull { it.slot == actorSlot }?.request

                assertEquals(context, expectedSpecies[actorName], checkNotNull(spriteRequest).species)
                assertEquals(
                    context,
                    if (playerSide) BattleSpriteSide.PLAYER else BattleSpriteSide.OPPONENT,
                    checkNotNull(spriteRequest).side
                )
                val moveMessage = session.battleFeedMessages().lastOrNull { it.text.contains(" used ") }?.text
                assertEquals(context, actorName, messageActor(checkNotNull(moveMessage)))
                moveCount += 1
            }
        }

        assertEquals(9, moveCount)
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
                    protocolMove.line,
                    protocolMove.actorName,
                    actorSlot,
                    protocolMove.actorSpecies,
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
                        val context = "$replayId at ${expectedMove.line}"
                        observedPacketMoveMessages += expectedMove.messageId
                        assertEquals(context, expectedMove.actorName.lowercase(), messageActor(frame.visibleText).lowercase())
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
                        val visibleActor = visibleCombatants.singleOrNull { it.slot == expectedMove.actorSlot }
                        val frameContext = "$context was rendered at simulated time $frameTimeMillis for $packet with message ${frame.messageId}"
                        assertTrue(
                            "$frameContext while active Pokémon are ${visibleCombatants.map { it.name }}",
                            visibleActor != null
                        )
                        val activeActor = checkNotNull(visibleActor)
                        assertEquals(frameContext, expectedMove.actorName.lowercase(), activeActor.name.lowercase())
                        assertEquals(frameContext, expectedMove.actorSpecies, activeActor.species)
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
                                .singleOrNull { it.slot == expectedMove.actorSlot }
                                ?.request
                            else -> spriteRequests.opponentActive
                                .singleOrNull { it.slot == expectedMove.actorSlot }
                                ?.request
                        }
                        assertEquals(frameContext, expectedMove.actorSpecies, checkNotNull(renderedSprite).species)
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
            "$replayId at ${expectedMove.line} was not shown during its action dwell",
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
}
