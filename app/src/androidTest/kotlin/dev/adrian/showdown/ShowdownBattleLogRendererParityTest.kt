package dev.adrian.showdown

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class ShowdownBattleLogRendererParityTest {
    @get:Rule
    val activityRule = ActivityScenarioRule(ShowdownLogParityHarnessActivity::class.java)

    @Test
    fun nativeNarrationKeepsMultiBattlePartnerOnTheLocalSide() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity {
            activity = it
            it.renderer.setPerspective("p3")
        }
        val transcript = listOf(
            "|player|p1|PARTNER||",
            "|player|p2|FOE||",
            "|player|p3|ALLY||",
            "|player|p4|FOE2||",
            "|gametype|multi",
            "|switch|p1a: Partner|Incineroar, L50|100/100",
            "|switch|p2a: Foe|Tapu Koko, L50|100/100",
            "|switch|p3a: Local|Mimikyu, L50|100/100",
            "|switch|p4a: Foe2|Landorus, L50|100/100",
            "|move|p1a: Partner|Flare Blitz|p2a: Foe",
            "|-damage|p2a: Foe|80/100"
        )
        val session = BattleSession().apply {
            setLocalUsername("ALLY")
            setReplayMode(true)
        }
        val narrationIdentityFailures = mutableListOf<String>()

        BattlePlaybackTiming.chunks(transcript).forEach { packet ->
            val entryCount = activity.nativeEntries.size
            val syncCount = activity.synchronizedGenerations.size
            val combatantsBefore = battleCombatants(session)
            session.applyProtocolPacket(packet)
            val generation = session.battleLogGeneration()
            activity.renderer.applyProtocol(packet, generation)
            awaitSynchronization(activity, syncCount, generation)
            val nativeEntries = activity.nativeEntries.drop(entryCount)
                .filter { it.first == generation }
                .map { it.second }
            if (nativeEntries.isNotEmpty()) {
                session.appendShowdownBattleLog(nativeEntries.joinToString("<br />"), generation)
            }
            session.markNativeBattleLogSynchronized(generation)
            val nativeTexts = nativeEntries.flatMap(ShowdownBattleLogFilter::visibleEntries)
            val eventCombatants = (combatantsBefore + battleCombatants(session))
                .distinctBy { Triple(it.slot, it.name, it.species) }
            assertNativePokemonNarrationUsesMatchingScene(
                session,
                nativeTexts,
                eventCombatants,
                narrationIdentityFailures
            )
        }

        assertTrue(
            "Multi partner narration was paired with the wrong Pokémon side: $narrationIdentityFailures",
            narrationIdentityFailures.isEmpty()
        )
    }

    @Test
    fun officialDoublesReplayNarrationMatchesItsVisibleCombatants() {
        assertOfficialReplayNarrationMatchesItsVisibleCombatants(
            "gen9doublesou-2691960998.json",
            requiredPlayerSlots = setOf("p1a", "p1b"),
            requiredOpponentSlots = setOf("p2a", "p2b")
        )
    }

    @Test
    fun officialFreeForAllReplayNarrationMatchesAllFourCombatantSlots() {
        assertOfficialReplayNarrationMatchesItsVisibleCombatants(
            "gen9freeforallrandombattle-2547390602.json",
            requiredPlayerSlots = setOf("p1a"),
            requiredOpponentSlots = setOf("p2a", "p3b", "p4b")
        )
    }

    @Test
    fun testUpstreamReplayNarrationKeepsTheCorrectCombatantSnapshot() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity { activity = it }
        val replayJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open("gen9randombattle-2691989691.json")
            .bufferedReader()
            .use { it.readText() }
        val replay = ShowdownReplayImporter.payload(replayJson)
        val lines = replay.log.lines()
        val whiteHerbLine = lines.indexOfFirst { it.startsWith("|-enditem|p1a: Minior|White Herb") }
        assertTrue("The saved official replay must include Minior's White Herb event", whiteHerbLine >= 0)
        val playbackLines = listOf("|init|battle") + lines
        val session = BattleSession().apply {
            setLocalUsername(replay.players.first())
            setReplayMode(true)
        }
        val expectedLeftoversText = "The opposing Salazzle restored a little HP using its Leftovers!"
        val expectedEncoreText = "The opposing Salazzle must do an encore!"
        val expectedWhiteHerbText = "Minior returned its stats to normal using its White Herb!"
        var sawLeftovers = false
        var sawEncore = false
        var sawWhiteHerb = false
        var sawReplayWinner = false
        val narrationIdentityFailures = mutableListOf<String>()

        BattlePlaybackTiming.chunks(playbackLines).forEach { packet ->
            val entryCount = activity.nativeEntries.size
            val syncCount = activity.synchronizedGenerations.size
            val combatantsBefore = battleCombatants(session)
            session.applyProtocolPacket(packet)
            val generation = session.battleLogGeneration()
            activity.renderer.applyProtocol(packet, generation)
            awaitSynchronization(activity, syncCount, generation)
            val nativeEntries = activity.nativeEntries.drop(entryCount)
                .filter { it.first == generation }
                .map { it.second }
            if (nativeEntries.isNotEmpty()) {
                session.appendShowdownBattleLog(nativeEntries.joinToString("<br />"), generation)
            }
            session.markNativeBattleLogSynchronized(generation)
            val nativeTexts = nativeEntries.flatMap(ShowdownBattleLogFilter::visibleEntries)
            val eventCombatants = (combatantsBefore + battleCombatants(session))
                .distinctBy { Triple(it.slot, it.name, it.species) }
            assertNativePokemonNarrationUsesMatchingScene(
                session,
                nativeTexts,
                eventCombatants,
                narrationIdentityFailures
            )

            if (packet.any { it.startsWith("|-heal|p2a: Salazzle|55/249") }) {
                assertTrue("Upstream Showdown did not render its known Leftovers narration: $nativeTexts", expectedLeftoversText in nativeTexts)
                sawLeftovers = true
            }
            if (packet.any { it.startsWith("|-start|p2a: Salazzle|Encore") }) {
                assertTrue("Upstream Showdown did not render its Encore narration: $nativeTexts", nativeTexts.any { it.contains("Salazzle", true) && it.contains("encore", true) })
                sawEncore = true
            }
            if (packet.any { it.startsWith("|-enditem|p1a: Minior|White Herb") }) {
                assertTrue("Upstream Showdown did not render its known White Herb narration: $nativeTexts", expectedWhiteHerbText in nativeTexts)
                sawWhiteHerb = true
            }
            if (packet.any { it.startsWith("|win|qiuescent") }) {
                assertTrue("Upstream Showdown did not render the replay's winner: $nativeTexts", nativeTexts.any { it.contains("won the battle", true) })
                sawReplayWinner = true
            }
        }

        assertTrue("The real renderer never emitted the Leftovers event", sawLeftovers)
        assertTrue("The real renderer never emitted the Encore event", sawEncore)
        assertTrue("The real renderer never emitted the White Herb event", sawWhiteHerb)
        assertTrue("The complete official replay never reached its winner event", sawReplayWinner)
        assertTrue("The native replay session did not finish", session.isBattleFinished())
        assertTrue("Native Showdown battle lines lost their protocol scene identities: $narrationIdentityFailures", narrationIdentityFailures.isEmpty())
    }

    private fun awaitSynchronization(
        activity: ShowdownLogParityHarnessActivity,
        previousCount: Int,
        generation: Long
    ) {
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2)
        while (
            activity.synchronizedGenerations.drop(previousCount).none { it == generation } &&
            System.currentTimeMillis() < deadline
        ) {
            Thread.sleep(25L)
        }
        assertTrue(
            "The upstream renderer did not finish protocol generation $generation; synchronized ${activity.synchronizedGenerations.drop(previousCount)}",
            activity.synchronizedGenerations.drop(previousCount).any { it == generation }
        )
    }

    private fun assertOfficialReplayNarrationMatchesItsVisibleCombatants(
        replayFileName: String,
        requiredPlayerSlots: Set<String>,
        requiredOpponentSlots: Set<String>
    ) {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity {
            activity = it
            it.renderer.setPerspective("p1")
        }
        val replayJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open(replayFileName)
            .bufferedReader()
            .use { it.readText() }
        val replay = ShowdownReplayImporter.payload(replayJson)
        val playbackLines = listOf("|init|battle") + replay.log.lines()
        val session = BattleSession().apply {
            setLocalUsername(replay.players.first())
            setReplayMode(true)
        }
        val narrationIdentityFailures = mutableListOf<String>()
        var renderedMoveCount = 0
        var sawRequiredCombatantSlots = false

        BattlePlaybackTiming.chunks(playbackLines).forEach { packet ->
            val entryCount = activity.nativeEntries.size
            val syncCount = activity.synchronizedGenerations.size
            val combatantsBefore = battleCombatants(session)
            session.applyProtocolPacket(packet)
            val generation = session.battleLogGeneration()
            activity.renderer.applyProtocol(packet, generation)
            awaitSynchronization(activity, syncCount, generation)
            val nativeEntries = activity.nativeEntries.drop(entryCount)
                .filter { it.first == generation }
                .map { it.second }
            if (nativeEntries.isNotEmpty()) {
                session.appendShowdownBattleLog(nativeEntries.joinToString("<br />"), generation)
            }
            session.markNativeBattleLogSynchronized(generation)
            val nativeTexts = nativeEntries.flatMap(ShowdownBattleLogFilter::visibleEntries)
            renderedMoveCount += nativeTexts.count { it.contains(" used ", true) }
            val eventCombatants = (combatantsBefore + battleCombatants(session))
                .distinctBy { Triple(it.slot, it.name, it.species) }
            assertNativePokemonNarrationUsesMatchingScene(
                session,
                nativeTexts,
                eventCombatants,
                narrationIdentityFailures
            )
            val playerSlots = session.playerActiveCombatants().mapTo(mutableSetOf()) { it.slot }
            val opponentSlots = session.opponentActiveCombatants().mapTo(mutableSetOf()) { it.slot }
            if (requiredPlayerSlots.all(playerSlots::contains) && requiredOpponentSlots.all(opponentSlots::contains)) {
                sawRequiredCombatantSlots = true
            }
        }

        assertTrue("$replayFileName did not reach its winner event", session.isBattleFinished())
        assertTrue("$replayFileName rendered no move narration", renderedMoveCount > 0)
        assertTrue("$replayFileName never had its required active slot layout", sawRequiredCombatantSlots)
        assertTrue(
            "$replayFileName narration was paired with the wrong Pokémon scene: $narrationIdentityFailures",
            narrationIdentityFailures.isEmpty()
        )
    }

    private fun battleCombatants(session: BattleSession) =
        session.playerActiveCombatants() + session.opponentActiveCombatants()

    private fun assertNativePokemonNarrationUsesMatchingScene(
        session: BattleSession,
        nativeTexts: List<String>,
        eventCombatants: List<BattleSession.ActiveCombatant>,
        failures: MutableList<String>
    ) {
        nativeTexts.forEach { text ->
            val mentionedCombatants = eventCombatants.filter { combatant -> mentionsPokemon(text, combatant.name) }
            if (mentionedCombatants.isEmpty()) return@forEach
            val message = session.battleFeedMessages().lastOrNull {
                BattleFeedMessageIdentity.matches(it.text, text)
            }
            if (message == null) {
                failures += "No protocol feed entry matched '$text'"
                return@forEach
            }
            val messageId = message.id
            val snapshot = session.battleSceneSnapshotForFeedMessage(messageId)
            val feedDiagnostics = session.battleFeedMessages().takeLast(12).joinToString {
                "${it.id}:${it.text}:${session.battleSceneSnapshotForFeedMessage(it.id) != null}"
            }
            if (snapshot == null) {
                failures += "'$text' has no scene snapshot; protocol=${session.battleLog().takeLast(12)}; feed=$feedDiagnostics"
                return@forEach
            }
            val switchOutVisual = session.switchOutVisualForBattleFeed(messageId)
            val playerCombatants = BattleFeedSceneState.combatantsForMessage(
                snapshot.playerCombatants,
                true,
                switchOutVisual
            )
            val opponentCombatants = BattleFeedSceneState.combatantsForMessage(
                snapshot.opponentCombatants,
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

            mentionedCombatants.forEach { eventCombatant ->
                val playerSide = session.isLocalBattleSide(eventCombatant.slot)
                val combatants = if (playerSide) playerCombatants else opponentCombatants
                val combatant = combatants.singleOrNull { it.slot.equals(eventCombatant.slot, true) }
                assertNotNull("Showdown narration was paired with the wrong Pokémon slot: $text", combatant)
                val matchedCombatant = checkNotNull(combatant)
                assertEquals(text, eventCombatant.name, matchedCombatant.name)
                val sprite = when {
                    playerSide && spriteRequests.singlesBattle -> spriteRequests.playerLead
                    !playerSide && spriteRequests.singlesBattle -> spriteRequests.opponentLead
                    playerSide -> spriteRequests.playerActive.singleOrNull { it.slot == eventCombatant.slot }?.request
                    else -> spriteRequests.opponentActive.singleOrNull { it.slot == eventCombatant.slot }?.request
                }
                val matchedSprite = checkNotNull(sprite)
                assertEquals(text, matchedCombatant.species, matchedSprite.species)
                assertEquals(
                    text,
                    if (playerSide) BattleSpriteSide.PLAYER else BattleSpriteSide.OPPONENT,
                    matchedSprite.side
                )
            }
        }
    }

    private fun mentionsPokemon(text: String, name: String): Boolean =
        Regex("(?<![\\p{L}\\p{N}])${Regex.escape(name)}(?![\\p{L}\\p{N}])", RegexOption.IGNORE_CASE)
            .containsMatchIn(text)
}
