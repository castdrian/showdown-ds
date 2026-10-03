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
        val playerSide: Boolean,
        val messageId: Long
    )

    @Test
    fun officialReplayMoveActorsStayAlignedWithTheVisibleSprites() {
        listOf(
            ReplayCase("gen9randombattle-2691947817.json", emptySet()),
            ReplayCase("gen9doublesou-2691960998.json", emptySet()),
            ReplayCase("gen9randombattle-2691989691.json", setOf("-formechange", "-transform")),
            ReplayCase("gen9randombattle-2691985124.json", setOf("-formechange")),
            ReplayCase("gen9randombattle-2691982973.json", setOf("replace"))
        ).forEach { replayCase ->
            listOf(0.75f, 1f).forEach { speed ->
                assertReplayMoveActorsMatchSprites(replayCase, speed)
            }
        }
    }

    private fun assertReplayMoveActorsMatchSprites(replayCase: ReplayCase, speed: Float) {
        val replayJson = checkNotNull(javaClass.getResourceAsStream("/showdown-replays/${replayCase.fileName}"))
            .bufferedReader()
            .use { it.readText() }
        val replay = ShowdownReplayImporter.payload(replayJson)
        val observedIdentityTransitions = replay.log.lines().mapNotNull { line ->
            line.split('|').getOrNull(1)?.takeIf { it in replayCase.requiredIdentityTransitions }
        }.toSet()
        assertEquals(replayCase.fileName, replayCase.requiredIdentityTransitions, observedIdentityTransitions)
        val session = BattleSession().apply {
            setLocalUsername(replay.players.firstOrNull().orEmpty())
            setReplayMode(true)
        }
        val presentation = BattleFeedPresentation().apply { setPlaybackSpeed(speed) }
        val expectedMoves = linkedMapOf<Long, ExpectedMove>()
        var nowMillis = 0L
        var moveCount = 0

        BattlePlaybackTiming.chunks(replay.log.lines()).forEach { packet ->
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

            packet.filter { it.startsWith("|move|") }.forEach { line ->
                val fields = line.split('|')
                val actorIdent = fields.getOrElse(2) { "" }
                val actorSlot = actorIdent.substringBefore(':').trim()
                val actorName = actorIdent.substringAfter(": ", actorIdent).substringBefore(',').trim()
                val context = "${replay.id} at $line"
                val moveMessage = generatedMessages.firstOrNull { message ->
                    message.text.contains(" used ") && messageActor(message.text).equals(actorName, true)
                }
                assertTrue("$context produced no move feed message", moveMessage != null)
                val expectedMessage = checkNotNull(moveMessage)
                val playerSide = session.playerActiveCombatants().any { it.slot == actorSlot }
                val activeCombatants = if (playerSide) {
                    session.playerActiveCombatants()
                } else {
                    session.opponentActiveCombatants()
                }
                val activeActor = activeCombatants.singleOrNull { it.slot == actorSlot }
                assertTrue("$context has no active combatant for its actor", activeActor != null)
                val expectedActor = checkNotNull(activeActor)
                assertEquals(context, actorName.lowercase(), expectedActor.name.lowercase())

                expectedMoves[expectedMessage.id] = ExpectedMove(
                    line,
                    actorName,
                    actorSlot,
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
                        val context = "${replay.id} at ${expectedMove.line}"
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
                        assertEquals(frameContext, activeActor.species, checkNotNull(renderedSprite).species)
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
                    "${replay.id} at ${expectedMove.line} was not shown during its action dwell",
                    messageId in observedPacketMoveMessages
                )
            }
            nowMillis += pauseMillis
        }

        assertTrue("${replayCase.fileName} did not exercise enough move events", moveCount >= 10)
    }

    private fun messageActor(message: String): String = message
        .substringBefore(" used ")
        .substringAfterLast("'s ")
        .replaceFirst(Regex("^the opposing ", RegexOption.IGNORE_CASE), "")
        .trim()
}
