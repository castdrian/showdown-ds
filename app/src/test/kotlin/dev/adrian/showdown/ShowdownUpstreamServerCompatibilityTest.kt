package dev.adrian.showdown

import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class ShowdownUpstreamServerCompatibilityTest {
    @Test
    fun connectsToUpstreamServerAndJoinsLobby() {
        val webSocketUrl = System.getenv("SHOWDOWN_TEST_SERVER_URL")?.takeIf(String::isNotBlank)
        assumeTrue("Upstream Showdown server URL was not configured", webSocketUrl != null)

        val endpoint = ShowdownServerEndpoint.fromInput(webSocketUrl!!)
            ?: error("Configured Showdown server URL is invalid")
        val client = UpstreamBattleClient("PSClient", "PSClientPeer", endpoint, AtomicBoolean(false))

        try {
            client.connectToLobby()
            assertTrue(
                "The upstream server did not return the lobby title",
                client.protocolPackets.any { (roomId, lines) -> roomId == null && lines.any { it == "|title|Lobby" } }
            )
        } finally {
            client.close()
        }
    }

    @Test
    fun startsRandomBattleAndKeepsServerMoveActorsAlignedWithTheirSceneSprites() {
        val webSocketUrl = System.getenv("SHOWDOWN_TEST_SERVER_URL")?.takeIf(String::isNotBlank)
        assumeTrue("Upstream Showdown server URL was not configured", webSocketUrl != null)

        val endpoint = ShowdownServerEndpoint.fromInput(webSocketUrl!!)
            ?: error("Configured Showdown server URL is invalid")
        val moveObserved = AtomicBoolean(false)
        val challenger = UpstreamBattleClient("PSParityAlpha", "PSParityBeta", endpoint, moveObserved)
        val opponent = UpstreamBattleClient("PSParityBeta", "PSParityAlpha", endpoint, moveObserved)

        try {
            challenger.connectToLobby()
            opponent.connectToLobby()

            assertTrue(
                "The upstream server rejected the challenge command",
                challenger.connection.sendGlobal("/challenge PSParityBeta, gen9randombattle")
            )
            assertTrue(
                "The upstream server did not deliver the challenge",
                opponent.challengeReceived.await(20, TimeUnit.SECONDS)
            )
            assertTrue(
                "The upstream server rejected the challenge acceptance",
                opponent.connection.sendGlobal("/accept PSParityAlpha")
            )
            assertTrue(
                "The upstream server did not start the random battle for the challenger",
                challenger.battleStarted.await(30, TimeUnit.SECONDS)
            )
            assertTrue(
                "The upstream server did not start the random battle for the opponent",
                opponent.battleStarted.await(30, TimeUnit.SECONDS)
            )
            assertTrue(
                "The upstream battle did not reach a move for the challenger",
                challenger.firstMove.await(30, TimeUnit.SECONDS)
            )
            assertTrue(
                "The upstream battle did not reach a move for the opponent",
                opponent.firstMove.await(30, TimeUnit.SECONDS)
            )

            assertEquals(challenger.battleRoomIds().single(), opponent.battleRoomIds().single())
            assertMoveActorsMatchActiveCombatantsAndSprites(challenger)
            assertMoveActorsMatchActiveCombatantsAndSprites(opponent)
        } finally {
            challenger.close()
            opponent.close()
        }
    }

    private fun assertMoveActorsMatchActiveCombatantsAndSprites(client: UpstreamBattleClient) {
        assertTrue("${client.username} received no parsed move observations", client.moveObservations.isNotEmpty())
        client.moveObservations.forEach { move ->
            val context = "${move.protocolLine} event ${move.eventId} for ${client.username}"
            val snapshot = checkNotNull(move.message.sceneContext?.snapshot) {
                "$context produced no scene snapshot"
            }
            val playerCombatant = snapshot.playerCombatants.singleOrNull { it.slot.equals(move.actorSlot, true) }
            val opponentCombatant = snapshot.opponentCombatants.singleOrNull { it.slot.equals(move.actorSlot, true) }
            val combatant = playerCombatant ?: opponentCombatant
            assertNotNull("$context has no active scene combatant", combatant)
            assertEquals(context, move.actorName, combatant?.name)

            val requests = BattleSpriteRequests.forScene(
                playerCombatants = snapshot.playerCombatants,
                opponentCombatants = snapshot.opponentCombatants,
                singlesBattle = client.session.isSinglesBattle(),
                style = client.session.spriteStyle,
                playerFallbackSpecies = client.session.playerPokemon,
                opponentFallbackSpecies = client.session.opponentPokemon
            )
            val sprite = when {
                playerCombatant != null && requests.singlesBattle -> requests.playerLead
                opponentCombatant != null && requests.singlesBattle -> requests.opponentLead
                playerCombatant != null -> requests.playerActive.singleOrNull { it.slot.equals(move.actorSlot, true) }?.request
                else -> requests.opponentActive.singleOrNull { it.slot.equals(move.actorSlot, true) }?.request
            }

            assertEquals(context, combatant?.species, sprite?.species)
            assertEquals(
                context,
                if (playerCombatant != null) BattleSpriteSide.PLAYER else BattleSpriteSide.OPPONENT,
                sprite?.side
            )
            assertEquals(context, move.actorName, move.message.text.substringBefore(" used ").removePrefix("The opposing "))
            assertTrue(context, move.message.text.contains(move.moveName, ignoreCase = true))
        }
    }

    private data class MoveObservation(
        val protocolLine: String,
        val eventId: Long,
        val actorSlot: String,
        val actorName: String,
        val moveName: String,
        val message: BattleFeedMessage
    )

    private class UpstreamBattleClient(
        val username: String,
        private val opponentUsername: String,
        endpoint: ShowdownServerEndpoint,
        private val moveObserved: AtomicBoolean
    ) : ShowdownConnection.Listener, AutoCloseable {
        val session = BattleSession().apply { setLocalUsername(username) }
        val connected = CountDownLatch(1)
        val loginChallenge = CountDownLatch(1)
        val renamedGuest = CountDownLatch(1)
        val joinedLobby = CountDownLatch(1)
        val challengeReceived = CountDownLatch(1)
        val battleStarted = CountDownLatch(1)
        val firstMove = CountDownLatch(1)
        val protocolPackets = CopyOnWriteArrayList<Pair<String?, List<String>>>()
        val moveObservations = CopyOnWriteArrayList<MoveObservation>()
        val connection = ShowdownConnection(endpoint, this)
        private var protocolLineIdentities = emptyList<BattleSession.ProtocolLineIdentity>()

        init {
            session.addProtocolListener { _, identities -> protocolLineIdentities = identities }
        }

        override fun onConnectionStateChanged(state: ShowdownConnection.State, detail: String) {
            if (state == ShowdownConnection.State.CONNECTED) connected.countDown()
        }

        override fun onProtocol(roomId: String?, lines: List<String>) {
            protocolPackets += roomId to lines
            val previousMessageIds = session.battleFeedMessages().mapTo(mutableSetOf()) { it.id }
            session.applyProtocolPacket(lines)
            val packetLineIdentities = protocolLineIdentities
            val newMessages = session.battleFeedMessages().filter { it.id !in previousMessageIds }

            if (lines.any { it.startsWith("|challstr|") }) loginChallenge.countDown()
            if (lines.any { it.startsWith("|updateuser| $username|1|") }) renamedGuest.countDown()
            if (roomId == null && lines.any { it == "|init|chat" }) joinedLobby.countDown()
            if (lines.any {
                    it.startsWith("|pm|") && it.contains(opponentUsername, true) &&
                        it.contains("/challenge gen9randombattle|", true)
                }) {
                challengeReceived.countDown()
            }
            if (roomId?.startsWith("battle-") == true && lines.any { it == "|init|battle" }) {
                battleStarted.countDown()
            }

            if (roomId?.startsWith("battle-") == true) {
                lines.filter { it.startsWith("|request|") }.forEach { requestLine ->
                    val request = runCatching { JSONObject(requestLine.removePrefix("|request|")) }.getOrNull()
                    if (request != null && !request.optBoolean("wait") && !moveObserved.get()) {
                        connection.send(roomId, "/choose default")
                    }
                }
                val packetMoveObservations = lines.mapIndexedNotNull { lineIndex, protocolLine ->
                    if (!protocolLine.startsWith("|move|")) return@mapIndexedNotNull null
                    val fields = protocolLine.split('|')
                    val actor = fields.getOrNull(2).orEmpty()
                    val moveName = fields.getOrNull(3).orEmpty()
                    val actorSlot = actor.substringBefore(':').trim()
                    val actorName = actor.substringAfter(':', "").trim()
                    val lineIdentity = packetLineIdentities.getOrNull(lineIndex)
                        ?: return@mapIndexedNotNull null
                    val message = lineIdentity.messageIds.asSequence()
                        .mapNotNull { messageId -> newMessages.firstOrNull { it.id == messageId } }
                        .lastOrNull {
                            it.text.substringBefore(" used ").removePrefix("The opposing ").equals(actorName, true) &&
                                it.text.contains(" used ", true) && it.text.contains(moveName, true)
                        }
                    if (message == null) {
                        null
                    } else {
                        MoveObservation(protocolLine, lineIdentity.eventId, actorSlot, actorName, moveName, message)
                    }
                }
                moveObservations += packetMoveObservations
                if (packetMoveObservations.isNotEmpty()) {
                    moveObserved.set(true)
                    firstMove.countDown()
                }
            }
        }

        fun connectToLobby() {
            connection.connect()
            assertTrue("$username did not connect to the upstream server", connected.await(20, TimeUnit.SECONDS))
            assertTrue("$username did not receive the upstream login challenge", loginChallenge.await(20, TimeUnit.SECONDS))
            assertTrue("$username guest rename was rejected", connection.sendGlobal("/trn $username,0,"))
            assertTrue("$username was not renamed by the upstream server", renamedGuest.await(20, TimeUnit.SECONDS))
            assertTrue("$username lobby join was rejected", connection.sendGlobal("/join lobby"))
            assertTrue("$username did not join the upstream lobby", joinedLobby.await(20, TimeUnit.SECONDS))
        }

        fun battleRoomIds() = protocolPackets.mapNotNull { (roomId, _) ->
            roomId?.takeIf { it.startsWith("battle-") }
        }.distinct()

        override fun close() {
            connection.close()
        }
    }
}
