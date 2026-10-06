package dev.adrian.showdown

import android.graphics.Bitmap
import android.graphics.Canvas
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
    private data class ProtocolMoveActor(
        val line: String,
        val slot: String,
        val name: String
    )

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
            assertNativeMoveNarrationMatchesProtocolActors(
                session,
                packet,
                nativeTexts,
                narrationIdentityFailures
            )
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
    fun upstreamMoveWithRepeatedNicknamesKeepsItsProtocolSlotSpriteIdentity() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity {
            activity = it
            it.renderer.setPerspective("p1")
        }
        val transcript = listOf(
            "|init|battle",
            "|player|p1|RED||",
            "|player|p2|BLUE||",
            "|gametype|doubles",
            "|switch|p1a: Snorlax|Snorlax, L50|100/100",
            "|switch|p1b: Togekiss|Togekiss, L50|100/100",
            "|switch|p2a: Sparky|Pikachu, L50|100/100",
            "|switch|p2b: Sparky|Raichu, L50|100/100",
            "|move|p2b: Sparky|Thunderbolt|p1a: Snorlax",
            "|-damage|p1a: Snorlax|80/100"
        )
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
        }
        val failures = mutableListOf<String>()

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
            assertNativeMoveNarrationMatchesProtocolActors(session, packet, nativeTexts, failures)
            val eventCombatants = (combatantsBefore + battleCombatants(session))
                .distinctBy { Triple(it.slot, it.name, it.species) }
            assertNativePokemonNarrationUsesMatchingScene(session, nativeTexts, eventCombatants, failures)
        }

        val moveMessage = session.battleFeedMessages().last { it.text.contains("Thunderbolt!") }
        val snapshot = checkNotNull(session.battleSceneSnapshotForFeedMessage(moveMessage.id))
        val actor = snapshot.opponentCombatants.single { it.slot == "p2b" }
        val actorSprite = BattleSpriteRequests.active(
            snapshot.opponentCombatants,
            BattleSpriteSide.OPPONENT,
            session.spriteStyle
        ).single { it.slot == "p2b" }.request

        assertEquals("Sparky", actor.name)
        assertEquals("Raichu", actor.species)
        assertEquals("Raichu", actorSprite.species)
        assertEquals(BattleSpriteSide.OPPONENT, actorSprite.side)
        assertTrue("Repeated-nickname upstream narration mismatched the scene: $failures", failures.isEmpty())
    }

    @Test
    fun liveOpponentPartySceneAndSpriteKeepTheSameIdentity() {
        val session = BattleSession().apply {
            setLocalUsername("RED")
        }
        session.applyProtocolPacket(
            listOf(
                "|init|battle",
                "|player|p1|RED||",
                "|player|p2|BLUE||",
                "|gametype|singles",
                "|clearpoke",
                "|poke|p2|Umbreon, L84, F|item",
                "|switch|p1a: Arbok|Arbok, L87|246/246",
                "|switch|p2a: Nocturne|Umbreon, L84, F|100/100",
                "|move|p2a: Nocturne|Foul Play|p1a: Arbok"
            )
        )

        val partyMember = session.opponentPartyDetails().single()
        val moveMessage = session.battleFeedMessages().last { it.text.contains("used Foul Play!") }
        val scene = checkNotNull(session.battleSceneSnapshotForFeedMessage(moveMessage.id))
        val activeCombatant = scene.opponentCombatants.single()
        val sprite = BattleSpriteRequests.forScene(
            playerCombatants = scene.playerCombatants,
            opponentCombatants = scene.opponentCombatants,
            singlesBattle = true,
            style = session.spriteStyle,
            playerFallbackSpecies = session.playerPokemon,
            opponentFallbackSpecies = session.opponentPokemon
        ).opponentLead

        assertEquals("Nocturne", partyMember.name)
        assertEquals("Umbreon", partyMember.species)
        assertEquals("In battle", session.opponentTeamCardStatus(0))
        assertEquals(partyMember.name, activeCombatant.name)
        assertEquals(partyMember.species, activeCombatant.species)
        assertEquals(partyMember.species, scene.opponentPartyDetails.single().species)
        assertEquals(partyMember.species, sprite?.species)
        assertEquals(BattleSpriteSide.OPPONENT, sprite?.side)
    }

    @Test
    fun battleSceneViewUsesQueuedSceneAfterItsProtocolSnapshotLeavesTheLogWindow() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity { activity = it }
        val session = BattleSession().apply {
            setLocalUsername("RED")
            setReplayMode(true)
            setLiveBattleActive(true)
            applyProtocolPacket(
                listOf(
                    "|init|battle",
                    "|player|p1|RED||",
                    "|player|p2|BLUE||",
                    "|switch|p1a: Rotom|Rotom, L50|100/100",
                    "|switch|p2a: Goodra|Goodra, L50|100/100"
                )
            )
        }
        val spriteCache = ShowdownSpriteCache(activity)
        val bitmap = Bitmap.createBitmap(960, 540, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        lateinit var sceneView: BattleSceneView
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            sceneView = BattleSceneView(activity, session, spriteCache)
            sceneView.setPlaybackSpeed(1f)
            sceneView.layout(0, 0, bitmap.width, bitmap.height)
            val requests = BattleSpriteRequests.forScene(
                session.playerActiveCombatants(),
                session.opponentActiveCombatants(),
                true,
                session.spriteStyle,
                session.playerPokemon,
                session.opponentPokemon
            )
            val tracker = checkNotNull(privateField(sceneView, "spriteRequestTracker"))
            setPrivateField(tracker, "previousRequests", requests)
            setPrivateField(sceneView, "resourcesRequested", true)
            setPrivateField(sceneView, "requestedPlayerSprite", requests.playerLead)
            setPrivateField(sceneView, "requestedOpponentSprite", requests.opponentLead)
        }
        fun drawScene() = instrumentation.runOnMainSync { sceneView.draw(canvas) }

        try {
            drawScene()
            session.applyProtocolLine("|move|p1a: Rotom|Thunderbolt|p2a: Goodra")
            val queuedMove = session.battleFeedMessages().last { it.text == "Rotom used Thunderbolt!" }
            drawScene()

            repeat(40) { index ->
                session.applyProtocolLine("|-message|Battle event $index.")
                drawScene()
            }

            assertEquals(null, session.battleSceneSnapshotForFeedMessage(queuedMove.id))
            session.applyProtocolLine("|switch|p2a: Dragapult|Dragapult, L50|100/100")
            drawScene()
            Thread.sleep(1_300L)
            drawScene()

            assertEquals("Dragapult", session.opponentActiveCombatants().single().name)
            instrumentation.runOnMainSync {
                val displayedScene = privateField(sceneView, "displayedBattleSceneSnapshot") as BattleSession.BattleSceneSnapshot
                val opponentSpriteRequest = privateField(sceneView, "requestedOpponentSprite") as BattleSpriteRequest
                assertEquals(queuedMove.text, privateField(sceneView, "cachedBattleFeedVisibleText"))
                assertEquals("Rotom", displayedScene.playerCombatants.single().name)
                assertEquals("Goodra", displayedScene.opponentCombatants.single().name)
                assertEquals("Goodra", opponentSpriteRequest.species)
            }
        } finally {
            instrumentation.runOnMainSync { sceneView.releaseRetainedResources() }
            spriteCache.close()
            bitmap.recycle()
        }
    }

    @Test
    fun explicitAnimationEventsRestartTheSameMoveForEachHit() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity { activity = it }
        val session = BattleSession().apply {
            setLocalUsername("RED")
            applyProtocolPacket(
                listOf(
                    "|player|p1|RED||",
                    "|player|p2|BLUE||",
                    "|switch|p1a: Cinderace|Cinderace, L80|100/100",
                    "|switch|p2a: Dragapult|Dragapult, L80|100/100"
                )
            )
        }
        val spriteCache = ShowdownSpriteCache(activity)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        lateinit var sceneView: BattleSceneView
        instrumentation.runOnMainSync {
            sceneView = BattleSceneView(activity, session, spriteCache)
            sceneView.applyLightweightBattleProtocol(
                listOf("|move|p1a: Cinderace|Triple Axel|p2a: Dragapult")
            )
        }

        try {
            val firstHitStartedAt = privateField(sceneView, "lightweightMoveStartedAtNanos") as Long
            Thread.sleep(20L)
            instrumentation.runOnMainSync {
                sceneView.applyLightweightBattleProtocol(
                    listOf("|-anim|p1a: Cinderace|Triple Axel|p2a: Dragapult")
                )
            }
            val secondHitStartedAt = privateField(sceneView, "lightweightMoveStartedAtNanos") as Long
            Thread.sleep(20L)
            instrumentation.runOnMainSync {
                sceneView.applyLightweightBattleProtocol(
                    listOf("|-anim|p1a: Cinderace|Triple Axel|p2a: Dragapult")
                )
            }
            val thirdHitStartedAt = privateField(sceneView, "lightweightMoveStartedAtNanos") as Long

            assertTrue(secondHitStartedAt > firstHitStartedAt)
            assertTrue(thirdHitStartedAt > secondHitStartedAt)
        } finally {
            instrumentation.runOnMainSync { sceneView.releaseRetainedResources() }
            spriteCache.close()
        }
    }

    @Test
    fun lightweightReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("gen9randombattle-2691989691.json")
    }

    @Test
    fun legacyReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("gen1ou-2692779867.json")
    }

    @Test
    fun secondGenerationReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("gen2ou-2692782179.json")
    }

    @Test
    fun thirdGenerationReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("gen3ou-2692783639.json")
    }

    @Test
    fun fourthGenerationReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("smogtours-gen4ou-451112.json")
    }

    @Test
    fun fifthGenerationReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("gen5ou-2587294183.json")
    }

    @Test
    fun sixthGenerationReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("smogtours-gen6ou-625213.json")
    }

    @Test
    fun seventhGenerationReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("gen7ou-2220548756.json")
    }

    @Test
    fun eighthGenerationReplayNarrationMatchesTheUpstreamBattleLog() {
        assertReplayNarrationMatchesUpstream("gen8ou-2692756742.json")
    }

    private fun assertReplayNarrationMatchesUpstream(replayFileName: String) {
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
        val session = BattleSession().apply {
            setLocalUsername(replay.players.first())
            setReplayMode(true)
        }
        val packets = BattlePlaybackTiming.chunks(listOf("|init|battle") + replay.log.lines())
        val lightweightTexts = mutableListOf<String>()
        val nativeTexts = mutableListOf<String>()
        packets.forEach { packet ->
            val previousMessageIds = session.battleFeedMessages().mapTo(mutableSetOf()) { it.id }
            session.applyProtocolPacket(packet)
            lightweightTexts += session.battleFeedMessages()
                .filter { it.id !in previousMessageIds }
                .map { it.text }
            val entryCount = activity.nativeEntries.size
            val syncCount = activity.synchronizedGenerations.size
            val generation = session.battleLogGeneration()
            activity.renderer.applyProtocol(packet, generation)
            awaitSynchronization(activity, syncCount, generation)
            nativeTexts += activity.nativeEntries.drop(entryCount)
                .filter { it.first == generation }
                .flatMap { ShowdownBattleLogFilter.visibleEntries(it.second) }
                .filterNot { isTurnMarker(it) || it.startsWith("Battle timer is", true) }
        }
        val firstMismatch = (0 until maxOf(lightweightTexts.size, nativeTexts.size))
            .firstOrNull { index -> lightweightTexts.getOrNull(index) != nativeTexts.getOrNull(index) }

        assertTrue(
            "$replayFileName lightweight replay log diverged from upstream Showdown at $firstMismatch: " +
                "lightweight=${lightweightTexts.drop(firstMismatch ?: lightweightTexts.size).take(12)} " +
                "upstream=${nativeTexts.drop(firstMismatch ?: nativeTexts.size).take(12)}",
            firstMismatch == null
        )
        if (replayFileName == "gen2ou-2692782179.json") {
            assertEquals(
                listOf(
                    "[☆Cee o-o] what",
                    "[☆Cee o-o] it does that",
                    "[+Fentropy] just crit it",
                    "[☆Cee o-o] gg"
                ),
                session.chatMessages()
            )
        }
    }

    @Test
    fun upstreamNarrationKeepsP3PartnerOnPlayerOneSideInMultiBattle() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity {
            activity = it
            it.renderer.setPerspective("p1")
        }
        val transcript = listOf(
            "|init|battle",
            "|gametype|multi",
            "|player|p1|PLAYER1||",
            "|player|p2|PLAYER2||",
            "|player|p3|PLAYER3||",
            "|player|p4|PLAYER4||",
            "|start",
            "|switch|p1a: Quagsire|Quagsire, L91|321/321",
            "|switch|p2a: Iron Hands|Iron Hands, L77|364/364",
            "|switch|p3b: Granbull|Granbull, L88, M|302/302",
            "|switch|p4b: Hoopa|Hoopa, L86|278/278",
            "|move|p3b: Granbull|Play Rough|p2a: Iron Hands"
        )
        val session = BattleSession().apply {
            setLocalUsername("PLAYER1")
            setReplayMode(true)
        }
        val renderedMoves = mutableListOf<String>()

        BattlePlaybackTiming.chunks(transcript).forEach { packet ->
            val entryCount = activity.nativeEntries.size
            val syncCount = activity.synchronizedGenerations.size
            session.applyProtocolPacket(packet)
            val generation = session.battleLogGeneration()
            activity.renderer.applyProtocol(packet, generation)
            awaitSynchronization(activity, syncCount, generation)
            val nativeEntries = activity.nativeEntries.drop(entryCount)
                .filter { it.first == generation }
                .map { it.second }
            if (nativeEntries.isNotEmpty()) {
                session.appendShowdownBattleLog(nativeEntries.joinToString("<br />"), generation)
                renderedMoves += nativeEntries.flatMap(ShowdownBattleLogFilter::visibleEntries)
                    .filter { it.contains(" used ", true) }
            }
            session.markNativeBattleLogSynchronized(generation)
        }

        assertTrue("The test transcript must identify the Multi partner as local", session.isLocalBattleSide("p3b"))
        val failures = mutableListOf<String>()
        assertNativeMoveNarrationMatchesProtocolActors(
            session,
            listOf("|move|p3b: Granbull|Play Rough|p2a: Iron Hands"),
            renderedMoves,
            failures
        )
        assertNativePokemonNarrationUsesMatchingScene(
            session,
            renderedMoves,
            battleCombatants(session),
            failures
        )
        assertTrue("The upstream-rendered partner move must match its active Pokémon scene: $failures", failures.isEmpty())
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
    fun officialIllusionReplayNarrationMatchesItsVisibleCombatants() {
        assertOfficialReplayNarrationMatchesItsVisibleCombatants(
            "gen9randombattle-2691982973.json",
            requiredPlayerSlots = setOf("p1a"),
            requiredOpponentSlots = setOf("p2a")
        )
    }

    @Test
    fun officialMegaReplayNarrationMatchesItsVisibleCombatants() {
        assertOfficialReplayNarrationMatchesItsVisibleCombatants(
            "smogtours-gen6ou-625213.json",
            requiredPlayerSlots = setOf("p1a"),
            requiredOpponentSlots = setOf("p2a")
        )
    }

    @Test
    fun officialMultiReplayNarrationMatchesItsVisiblePartnersAndOpponents() {
        assertOfficialReplayNarrationMatchesItsVisibleCombatants(
            "gen9multirandombattle-2641276114.json",
            requiredPlayerSlots = setOf("p1a", "p3b"),
            requiredOpponentSlots = setOf("p2a", "p4b")
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
    fun upstreamReplayPlayerOneNarrationKeepsTheCorrectCombatantSnapshot() {
        assertUpstreamReplayNarrationKeepsTheCorrectCombatantSnapshot(localPlayerIndex = 0)
    }

    @Test
    fun upstreamReplayPlayerTwoNarrationKeepsTheCorrectCombatantSnapshot() {
        assertUpstreamReplayNarrationKeepsTheCorrectCombatantSnapshot(localPlayerIndex = 1)
    }

    private fun assertUpstreamReplayNarrationKeepsTheCorrectCombatantSnapshot(localPlayerIndex: Int) {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity {
            activity = it
            it.renderer.setPerspective(if (localPlayerIndex == 0) "p1" else "p2")
        }
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
            setLocalUsername(replay.players[localPlayerIndex])
            setReplayMode(true)
        }
        val expectedLeftoversText = if (localPlayerIndex == 0) {
            "The opposing Salazzle restored a little HP using its Leftovers!"
        } else {
            "Salazzle restored a little HP using its Leftovers!"
        }
        val expectedEncoreText = if (localPlayerIndex == 0) {
            "The opposing Salazzle must do an encore!"
        } else {
            "Salazzle must do an encore!"
        }
        val expectedWhiteHerbText = if (localPlayerIndex == 0) {
            "Minior returned its stats to normal using its White Herb!"
        } else {
            "The opposing Minior returned its stats to normal using its White Herb!"
        }
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
            assertNativeMoveNarrationMatchesProtocolActors(
                session,
                packet,
                nativeTexts,
                narrationIdentityFailures
            )
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

    private fun isTurnMarker(value: String): Boolean =
        Regex("^(?:Turn\\s+\\d+\\.?|==\\s*Turn\\s+\\d+\\s*==)$", RegexOption.IGNORE_CASE)
            .matches(value.trim())

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
            assertNativeMoveNarrationMatchesProtocolActors(
                session,
                packet,
                nativeTexts,
                narrationIdentityFailures
            )
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

    private fun assertNativeMoveNarrationMatchesProtocolActors(
        session: BattleSession,
        packet: List<String>,
        nativeTexts: List<String>,
        failures: MutableList<String>
    ) {
        val protocolActors = packet.mapNotNull { line ->
            val fields = line.split('|')
            if (fields.getOrNull(1) != "move") return@mapNotNull null
            if (line.contains("|[from] ability: Magic Bounce", true)) return@mapNotNull null
            val actorId = fields.getOrNull(2).orEmpty()
            val slot = actorId.substringBefore(':').trim()
            val name = actorId.substringAfter(": ", "").trim()
            if (slot.isEmpty() || name.isEmpty()) null else ProtocolMoveActor(line, slot, name)
        }
        if (protocolActors.isEmpty()) return

        val narratedMoves = nativeTexts.filter { it.contains(" used ", true) }
        if (protocolActors.size != narratedMoves.size) {
            failures += "Protocol move actors ${protocolActors.map { it.name }} did not match rendered move narration $narratedMoves; protocol=${protocolActors.map { it.line }}"
            return
        }
        protocolActors.zip(narratedMoves).forEach { (protocolMove, narration) ->
            val context = "protocol=${protocolMove.line}; narration='$narration'"
            if (!mentionsPokemon(narration, protocolMove.name)) {
                failures += "Protocol move actor '${protocolMove.name}' did not match rendered narration; $context"
                return@forEach
            }
            val message = session.battleFeedMessages().lastOrNull {
                BattleFeedMessageIdentity.matches(it.text, narration)
            }
            if (message == null) {
                failures += "No battle feed message matched protocol move actor '${protocolMove.name}'; $context"
                return@forEach
            }
            val snapshot = session.battleSceneSnapshotForFeedMessage(message.id)
            if (snapshot == null) {
                failures += "No scene snapshot matched protocol move actor '${protocolMove.name}'; $context"
                return@forEach
            }
            val playerSide = session.isLocalBattleSide(protocolMove.slot)
            val switchOutVisual = session.switchOutVisualForBattleFeed(message.id)
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
            val combatants = if (playerSide) playerCombatants else opponentCombatants
            val actor = combatants.singleOrNull { it.slot.equals(protocolMove.slot, true) }
            if (actor == null) {
                failures += "Protocol move actor slot '${protocolMove.slot}' was absent from its message scene; $context; player=${playerCombatants.map { it.slot to it.name }}; opponent=${opponentCombatants.map { it.slot to it.name }}"
                return@forEach
            }
            if (!actor.name.equals(protocolMove.name, true)) {
                failures += "Protocol move actor '${protocolMove.slot}:${protocolMove.name}' did not match the scene's '${actor.slot}:${actor.name}'; $context"
                return@forEach
            }
            val spriteRequests = BattleSpriteRequests.forScene(
                playerCombatants = playerCombatants,
                opponentCombatants = opponentCombatants,
                singlesBattle = session.isSinglesBattle(),
                style = session.spriteStyle,
                playerFallbackSpecies = session.playerPokemon,
                opponentFallbackSpecies = session.opponentPokemon
            )
            val sprite = when {
                playerSide && spriteRequests.singlesBattle -> spriteRequests.playerLead
                !playerSide && spriteRequests.singlesBattle -> spriteRequests.opponentLead
                playerSide -> spriteRequests.playerActive.singleOrNull { it.slot == protocolMove.slot }?.request
                else -> spriteRequests.opponentActive.singleOrNull { it.slot == protocolMove.slot }?.request
            }
            if (sprite == null) {
                failures += "Protocol move actor '${protocolMove.slot}:${protocolMove.name}' had no visible sprite request; $context"
                return@forEach
            }
            if (sprite.species != actor.species) {
                failures += "Protocol move actor '${protocolMove.slot}:${protocolMove.name}' showed ${sprite.species}, expected ${actor.species}; $context"
            }
            val saysOpposing = narration.startsWith("The opposing ", true)
            val multiPartnerUsesShowdownOpposingLabel = session.gameType.equals("multi", true) &&
                playerSide &&
                !protocolMove.slot.take(2).equals(session.battlePlayerSlot(), true)
            if (!multiPartnerUsesShowdownOpposingLabel && saysOpposing == playerSide) {
                failures += "Protocol move actor '${protocolMove.slot}:${protocolMove.name}' narration used the wrong battle side; $context"
            }
        }
    }

    private fun assertNativePokemonNarrationUsesMatchingScene(
        session: BattleSession,
        nativeTexts: List<String>,
        eventCombatants: List<BattleSession.ActiveCombatant>,
        failures: MutableList<String>
    ) {
        nativeTexts.forEach { text ->
            val mentionedCombatants = eventCombatants.filter { combatant -> mentionsPokemon(text, combatant.name) }
            if (mentionedCombatants.isEmpty()) {
                if (text.contains(" used ", true)) {
                    val activeCombatants = eventCombatants.joinToString { "${it.slot}:${it.name}/${it.species}" }
                    failures += "Move narration did not name an active combatant: '$text'; active=$activeCombatants"
                }
                return@forEach
            }
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

    private fun privateField(target: Any, name: String): Any? = target.javaClass.getDeclaredField(name)
        .apply { isAccessible = true }
        .get(target)

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }
            .set(target, value)
    }
}
