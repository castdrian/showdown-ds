package dev.adrian.showdown

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityReplayPlaybackParityTest {
    private data class ReplayFrameObservation(
        val text: String,
        val message: BattleFeedMessage,
        val displayedScene: BattleSession.BattleSceneSnapshot?,
        val displayedSwitchOutVisual: BattleSession.SwitchOutVisual?,
        val spriteStyle: BattleSession.SpriteStyle,
        val playerSprite: BattleSpriteRequest?,
        val opponentSprite: BattleSpriteRequest?,
        val singlesBattle: Boolean = true,
        val playerActiveSprites: Map<String, BattleSpriteRequest> = emptyMap(),
        val opponentActiveSprites: Map<String, BattleSpriteRequest> = emptyMap()
    )

    private data class MultiReplayMoveExpectation(
        val slot: String,
        val actorName: String,
        val moveName: String,
        val side: BattleSpriteSide,
        val expectedOpposingPrefix: Boolean
    )

    @Test
    fun productionReplayKeepsEachVisibleLogEntryPairedWithItsDisplayedPokemon() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val replayJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open("gen9randombattle-2691989691.json")
            .bufferedReader()
            .use { it.readText() }
        val completeReplay = ShowdownReplayImporter.payload(replayJson)
        val replayLines = completeReplay.log.lines()
        val finalReplayLine = replayLines.indexOfFirst { it.startsWith("|-hitcount|p1a: Dewgong|3") }
        assertTrue("The official replay fixture is missing its expected multi-hit event", finalReplayLine >= 0)
        val replay = completeReplay.copy(log = replayLines.take(finalReplayLine + 1).joinToString("\n"))
        val preferences = targetContext.getSharedPreferences("showdown_live", 0)
        val previousConnectionPreference = preferences.getBoolean("maintain_connection", false)
        val observedFrames = linkedMapOf<Long, ReplayFrameObservation>()
        var latestNativeLog = emptyList<String>()
        var latestProtocolLog = emptyList<String>()
        var latestNativeGeneration = -1L
        var latestProtocolGeneration = -1L
        var latestPlaybackState = ""
        var targetVisibleAt: Long? = null
        var targetNativeCaughtUp = false
        val playbackTimeline = mutableListOf<String>()
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(90)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            preferences.edit().putBoolean("maintain_connection", false).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val activeScenario = checkNotNull(scenario)
            activeScenario.onActivity { activity ->
                setPrivateField(activity, "restoredReplaySpeed", BattlePlaybackSpeed.MAXIMUM)
                setPrivateField(activity, "lightweightBattlePlayback", false)
                MainActivity::class.java.getDeclaredMethod("showReplay", ShowdownReplayPayload::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, replay)
                assertNotNull(
                    "The replay did not create the native Showdown renderer",
                    privateField(activity, "showdownMoveEffects")
                )
            }

            while (SystemClock.elapsedRealtime() < deadline &&
                (targetVisibleAt == null || (!targetNativeCaughtUp &&
                    SystemClock.elapsedRealtime() - checkNotNull(targetVisibleAt) < TimeUnit.SECONDS.toMillis(15)))
            ) {
                activeScenario.onActivity { activity ->
                    val session = privateField(activity, "session") as BattleSession
                    val scene = privateField(activity, "battleScene") as BattleSceneView
                    val feedPresentation = checkNotNull(privateField(scene, "battleFeedPresentation"))
                    latestNativeLog = session.showdownBattleLog()
                    latestProtocolLog = session.battleLog()
                    latestNativeGeneration = privateField(session, "nativeBattleLogGeneration") as Long
                    latestProtocolGeneration = session.battleLogGeneration()
                    scene.invalidate()
                    val text = privateField(scene, "cachedBattleFeedVisibleText") as? String ?: return@onActivity
                    val message = privateField(feedPresentation, "currentMessage") as? BattleFeedMessage ?: return@onActivity
                    if (message.text != text) return@onActivity
                    if (text.contains("Triple Axel", true) && text.contains("Weavile", true) && targetVisibleAt == null) {
                        targetVisibleAt = SystemClock.elapsedRealtime()
                    }
                    targetNativeCaughtUp = latestNativeGeneration == latestProtocolGeneration &&
                        latestNativeLog.any { it.contains("Triple Axel", true) && it.contains("Weavile", true) }
                    val pendingMessages = privateField(feedPresentation, "pendingMessages") as? Collection<*>
                    val pendingPackets = privateField(activity, "pendingBattlePackets") as? Collection<*>
                    val state = "visible=$text#${message.id}; protocolGen=$latestProtocolGeneration; " +
                        "nativeGen=$latestNativeGeneration; nativeTail=${latestNativeLog.takeLast(3)}; " +
                        "protocolTail=${latestProtocolLog.takeLast(3)}; feedPending=${pendingMessages?.size}; " +
                        "packetPending=${pendingPackets?.size}; scheduled=${privateField(activity, "battlePacketPlaybackScheduled")}; " +
                        "barrier=${privateField(privateField(activity, "battlePlaybackBarrier")!!, "awaitedToken")}; " +
                        "stalled=${privateField(activity, "rendererRecoveryStalled")}; lightweight=${privateField(activity, "lightweightBattlePlayback")}"
                    if (playbackTimeline.lastOrNull() != state) playbackTimeline.add(state)
                    latestPlaybackState = state
                    observedFrames[message.id] = ReplayFrameObservation(
                        text = text,
                        message = message,
                        displayedScene = privateField(scene, "displayedBattleSceneSnapshot") as? BattleSession.BattleSceneSnapshot,
                        displayedSwitchOutVisual = privateField(scene, "displayedSwitchOutVisual") as? BattleSession.SwitchOutVisual,
                        spriteStyle = session.spriteStyle,
                        playerSprite = privateField(scene, "requestedPlayerSprite") as? BattleSpriteRequest,
                        opponentSprite = privateField(scene, "requestedOpponentSprite") as? BattleSpriteRequest
                    )
                }
                Thread.sleep(40L)
            }

            val observedTexts = observedFrames.values.map(ReplayFrameObservation::text)
            assertTrue(
                "The production replay never displayed its expected multi-hit Pokémon action. " +
                    "visible=$observedTexts; state=$latestPlaybackState; native=$latestNativeLog; protocol=$latestProtocolLog",
                observedTexts.any { it.contains("Triple Axel", true) && it.contains("Weavile", true) }
            )
            assertTrue(
                "The native Showdown renderer did not catch up to its visible multi-hit action within 15 seconds. " +
                    "state=$latestPlaybackState; timeline=${playbackTimeline.takeLast(40)}; native=$latestNativeLog; protocol=$latestProtocolLog",
                targetNativeCaughtUp
            )
            assertTrue("The production replay skipped several visible Showdown entries: $observedTexts", observedFrames.size >= 6)
            assertTrue("The production replay did not display the status action before the hit: $observedTexts", observedTexts.any { it.contains("Protect", true) })
            observedFrames.values.forEach { frame ->
                assertEquals(frame.text, frame.message.text)
                assertEquals(frame.message.sceneContext?.snapshot, frame.displayedScene)
                assertEquals(frame.message.sceneContext?.switchOutVisual, frame.displayedSwitchOutVisual)
                assertVisiblePokemonNamesBelongToDisplayedScene(frame)
                val snapshot = frame.displayedScene ?: return@forEach
                val switchOutVisual = frame.displayedSwitchOutVisual
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
                val expectedPlayer = BattleSpriteRequests.active(
                    playerCombatants,
                    BattleSpriteSide.PLAYER,
                    frame.spriteStyle
                ).firstOrNull()?.request
                val expectedOpponent = BattleSpriteRequests.active(
                    opponentCombatants,
                    BattleSpriteSide.OPPONENT,
                    frame.spriteStyle
                ).firstOrNull()?.request
                if (expectedPlayer != null) assertEquals(frame.text, expectedPlayer.species, frame.playerSprite?.species)
                if (expectedOpponent != null) assertEquals(frame.text, expectedOpponent.species, frame.opponentSprite?.species)
            }
        } finally {
            scenario?.close()
            preferences.edit().putBoolean("maintain_connection", previousConnectionPreference).commit()
        }
    }


    @Test
    fun productionReplayPairsIllusionRevealWithTheRevealedPokemonSprite() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val replayJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open("gen9randombattle-2691982973.json")
            .bufferedReader()
            .use { it.readText() }
        val completeReplay = ShowdownReplayImporter.payload(replayJson)
        val replayLines = completeReplay.log.lines()
        val startLine = replayLines.indexOf("|start")
        val replaceLine = replayLines.indexOfFirst { it.startsWith("|replace|p2a: Zoroark|") }
        val illusionEndLine = replayLines.indexOfFirst { it.startsWith("|-end|p2a: Zoroark|Illusion") }
        val disguisedSwitchLine = replayLines.indices.lastOrNull { index ->
            index < replaceLine && replayLines[index].startsWith("|switch|p2a: Sceptile|")
        } ?: -1
        val playerSwitchLine = replayLines.firstOrNull { it.startsWith("|switch|p1a: Snorlax|") }
        assertTrue("The official replay fixture is missing its battle start", startLine >= 0)
        assertTrue("The official replay fixture is missing its Zoroark Illusion reveal", replaceLine > startLine)
        assertTrue("The official replay fixture is missing the end of Zoroark's Illusion", illusionEndLine > replaceLine)
        assertTrue("The official replay fixture is missing Sceptile's disguised switch-in", disguisedSwitchLine > startLine)
        assertTrue("The official replay fixture is missing Snorlax's switch-in", playerSwitchLine != null)
        val playbackLines = replayLines.take(startLine + 1) +
            listOf(checkNotNull(playerSwitchLine), "|-status|p1a: Snorlax|slp") +
            replayLines.subList(disguisedSwitchLine, illusionEndLine + 1)
        val replay = completeReplay.copy(log = playbackLines.joinToString("\n"))
        val preferences = targetContext.getSharedPreferences("showdown_live", 0)
        val previousConnectionPreference = preferences.getBoolean("maintain_connection", false)
        var observedReveal: ReplayFrameObservation? = null
        var latestNativeLog = emptyList<String>()
        var latestProtocolLog = emptyList<String>()
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(90)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            preferences.edit().putBoolean("maintain_connection", false).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val activeScenario = checkNotNull(scenario)
            activeScenario.onActivity { activity ->
                setPrivateField(activity, "restoredReplaySpeed", BattlePlaybackSpeed.MAXIMUM)
                MainActivity::class.java.getDeclaredMethod("showReplay", ShowdownReplayPayload::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, replay)
            }

            while (SystemClock.elapsedRealtime() < deadline && observedReveal == null) {
                activeScenario.onActivity { activity ->
                    val session = privateField(activity, "session") as BattleSession
                    val scene = privateField(activity, "battleScene") as BattleSceneView
                    val feedPresentation = checkNotNull(privateField(scene, "battleFeedPresentation"))
                    latestNativeLog = session.showdownBattleLog()
                    latestProtocolLog = session.battleLog()
                    scene.invalidate()
                    val text = privateField(scene, "cachedBattleFeedVisibleText") as? String ?: return@onActivity
                    val message = privateField(feedPresentation, "currentMessage") as? BattleFeedMessage ?: return@onActivity
                    if (message.text != text || !text.contains("Zoroark", true) || !text.contains("Illusion", true)) {
                        return@onActivity
                    }
                    observedReveal = ReplayFrameObservation(
                        text = text,
                        message = message,
                        displayedScene = privateField(scene, "displayedBattleSceneSnapshot") as? BattleSession.BattleSceneSnapshot,
                        displayedSwitchOutVisual = privateField(scene, "displayedSwitchOutVisual") as? BattleSession.SwitchOutVisual,
                        spriteStyle = session.spriteStyle,
                        playerSprite = privateField(scene, "requestedPlayerSprite") as? BattleSpriteRequest,
                        opponentSprite = privateField(scene, "requestedOpponentSprite") as? BattleSpriteRequest
                    )
                }
                if (observedReveal == null) Thread.sleep(20L)
            }

            assertTrue(
                "The production replay never displayed the Zoroark Illusion reveal. " +
                    "native=$latestNativeLog; protocol=$latestProtocolLog",
                observedReveal != null
            )
            val frame = checkNotNull(observedReveal)
            assertEquals(frame.text, frame.message.text)
            assertEquals(frame.message.sceneContext?.snapshot, frame.displayedScene)
            assertEquals(frame.message.sceneContext?.switchOutVisual, frame.displayedSwitchOutVisual)
            val revealedPokemon = frame.displayedScene?.opponentCombatants?.singleOrNull { it.slot == "p2a" }
            assertEquals(frame.text, "Zoroark", revealedPokemon?.name)
            assertEquals(frame.text, "Zoroark", revealedPokemon?.species)
            assertEquals(frame.text, "Zoroark", frame.opponentSprite?.species)
        } finally {
            scenario?.close()
            preferences.edit().putBoolean("maintain_connection", previousConnectionPreference).commit()
        }
    }

    @Test
    fun productionMultiReplayKeepsEachMoveActorPairedWithItsVisibleSprite() {
        assertNativeProductionReplayMovesPairedWithVisibleSprites(
            replayFileName = "gen9multirandombattle-2641276114.json",
            finalMoveLinePrefix = "|move|p3b: Granbull|Play Rough|p2a: Iron Hands",
            expectedMoves = listOf(
                MultiReplayMoveExpectation("p2a", "Iron Hands", "Fake Out", BattleSpriteSide.OPPONENT, true),
                MultiReplayMoveExpectation("p1a", "Quagsire", "Yawn", BattleSpriteSide.PLAYER, false),
                MultiReplayMoveExpectation("p4b", "Vaporeon", "Yawn", BattleSpriteSide.OPPONENT, true),
                MultiReplayMoveExpectation("p3b", "Granbull", "Play Rough", BattleSpriteSide.PLAYER, false)
            )
        )
    }

    @Test
    fun productionDoublesReplayKeepsNativeMoveActorsPairedWithVisibleSprites() {
        assertNativeProductionReplayMovesPairedWithVisibleSprites(
            replayFileName = "gen9doublesou-2691960998.json",
            finalMoveLinePrefix = "|move|p1b: Blaziken|Aura Sphere|p2b: Porygon2",
            expectedMoves = listOf(
                MultiReplayMoveExpectation("p1a", "Delibird", "Fake Out", BattleSpriteSide.PLAYER, false),
                MultiReplayMoveExpectation("p1b", "Blaziken", "Aura Sphere", BattleSpriteSide.PLAYER, false)
            )
        )
    }

    @Test
    fun productionFreeForAllReplayKeepsNativeMoveActorsPairedWithVisibleSprites() {
        assertNativeProductionReplayMovesPairedWithVisibleSprites(
            replayFileName = "gen9freeforallrandombattle-2547390602.json",
            finalMoveLinePrefix = "|move|p3b: Galvantula|Sticky Web|p4b: Passimian",
            expectedMoves = listOf(
                MultiReplayMoveExpectation("p4b", "Passimian", "Rock Slide", BattleSpriteSide.OPPONENT, true),
                MultiReplayMoveExpectation("p3b", "Galvantula", "Sticky Web", BattleSpriteSide.OPPONENT, true)
            )
        )
    }

    private fun assertNativeProductionReplayMovesPairedWithVisibleSprites(
        replayFileName: String,
        finalMoveLinePrefix: String,
        expectedMoves: List<MultiReplayMoveExpectation>
    ) {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val replayJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open(replayFileName)
            .bufferedReader()
            .use { it.readText() }
        val completeReplay = ShowdownReplayImporter.payload(replayJson)
        val replayLines = completeReplay.log.lines()
        val finalReplayLine = replayLines.indexOfFirst { it.startsWith(finalMoveLinePrefix) }
        assertTrue("$replayFileName is missing its final expected opening move", finalReplayLine >= 0)
        val replay = completeReplay.copy(log = replayLines.take(finalReplayLine + 1).joinToString("\n"))
        val preferences = targetContext.getSharedPreferences("showdown_live", 0)
        val previousConnectionPreference = preferences.getBoolean("maintain_connection", false)
        val observedFrames = linkedMapOf<String, ReplayFrameObservation>()
        val displayedFrames = linkedMapOf<Long, ReplayFrameObservation>()
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var latestNativeLog = emptyList<String>()
        var latestProtocolLog = emptyList<String>()
        var latestPlaybackState = "unobserved"
        var expectedMovesObserved = false
        var nativeMovesObserved = false
        var nativeReplayCaughtUp = false
        var latestFeedState = emptyList<Pair<Long, String>>()
        var lastDrainedNativeGeneration = Long.MIN_VALUE
        var lastProgressState = ""
        var lastProgressAt = SystemClock.elapsedRealtime()
        var playbackStalled = false
        val playbackDeadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(60)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            preferences.edit().putBoolean("maintain_connection", false).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val activeScenario = checkNotNull(scenario)
            activeScenario.onActivity { activity ->
                setPrivateField(activity, "restoredReplaySpeed", BattlePlaybackSpeed.MAXIMUM)
                setPrivateField(activity, "lightweightBattlePlayback", false)
                MainActivity::class.java.getDeclaredMethod("showReplay", ShowdownReplayPayload::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, replay)
                assertNotNull(
                    "The native Showdown renderer was not created for $replayFileName",
                    privateField(activity, "showdownMoveEffects")
                )
            }

            while (SystemClock.elapsedRealtime() < playbackDeadline &&
                (!expectedMovesObserved || !nativeMovesObserved || !nativeReplayCaughtUp) && !playbackStalled
            ) {
                activeScenario.onActivity { activity ->
                    val session = privateField(activity, "session") as BattleSession
                    val scene = privateField(activity, "battleScene") as BattleSceneView
                    latestNativeLog = session.showdownBattleLog()
                    latestProtocolLog = session.battleLog()
                    val nativeGeneration = privateField(session, "nativeBattleLogGeneration") as Long
                    val feedState = session.battleFeedMessages(Int.MAX_VALUE).map { it.id to it.text }
                    if (feedState != latestFeedState || nativeGeneration != lastDrainedNativeGeneration) {
                        drainDisplayedReplayMessages(
                            activity,
                            expectedMoves.mapTo(linkedSetOf(), MultiReplayMoveExpectation::actorName),
                            emptyMap(),
                            displayedFrames,
                            canvas
                        )
                        latestFeedState = feedState
                        lastDrainedNativeGeneration = nativeGeneration
                    }
                    val pendingPackets = privateField(activity, "pendingBattlePackets") as? Collection<*>
                    val scheduled = privateField(activity, "battlePacketPlaybackScheduled") as Boolean
                    val activeBarrierToken = privateField(activity, "activeBattleEffectsBarrierToken")
                    val playbackBarrier = privateField(activity, "battlePlaybackBarrier") as BattlePlaybackBarrier
                    val effectsView = privateField(activity, "showdownMoveEffects") as ShowdownMoveEffectsView
                    nativeReplayCaughtUp = nativeGeneration == session.battleLogGeneration()
                    val observedMoveSlots = expectedMoves.filter { expectedMove ->
                        displayedFrames.values.any { frame ->
                            frame.text.contains(expectedMove.actorName, true) &&
                                frame.text.contains("used ${expectedMove.moveName}", true)
                        }
                    }.mapTo(linkedSetOf(), MultiReplayMoveExpectation::slot)
                    expectedMovesObserved = observedMoveSlots == expectedMoves.mapTo(
                        linkedSetOf(),
                        MultiReplayMoveExpectation::slot
                    )
                    nativeMovesObserved = expectedMoves.all { expectedMove ->
                        latestNativeLog.any {
                            it.contains(expectedMove.actorName, true) &&
                                it.contains("used ${expectedMove.moveName}", true)
                        }
                    }
                    latestPlaybackState = "paused=${privateField(activity, "replayPaused")}; " +
                        "protocolGeneration=${session.battleLogGeneration()}; nativeGeneration=$nativeGeneration; " +
                        "nativeCaughtUp=$nativeReplayCaughtUp; pending=${pendingPackets?.size}; " +
                        "scheduled=$scheduled; barrier=$activeBarrierToken; " +
                        "awaited=${privateField(playbackBarrier, "awaitedToken")}; " +
                        "recoveries=${playbackBarrier.recoveryAttempts()}; " +
                        "activityResumed=${privateField(activity, "activityResumed")}; " +
                        "effectsLoaded=${privateField(effectsView, "pageLoaded")}; " +
                        "effectsSize=${effectsView.width}x${effectsView.height}; " +
                        "effectsAttached=${effectsView.isAttachedToWindow}; " +
                        "rendererStalled=${privateField(activity, "rendererRecoveryStalled")}; " +
                        "lightweight=${privateField(activity, "lightweightBattlePlayback")}; " +
                        "visible=${privateField(scene, "cachedBattleFeedVisibleText")}; " +
                        "observed=${displayedFrames.size}; moveSlots=$observedMoveSlots; " +
                        "nativeTail=${latestNativeLog.takeLast(3)}"
                    val now = SystemClock.elapsedRealtime()
                    if (latestPlaybackState != lastProgressState) {
                        lastProgressState = latestPlaybackState
                        lastProgressAt = now
                    }
                    playbackStalled = now - lastProgressAt >= TimeUnit.SECONDS.toMillis(40)
                }
                Thread.sleep(100L)
            }

            displayedFrames.values.forEach { frame ->
                val expectedMove = expectedMoves.firstOrNull { candidate ->
                    frame.text.contains(candidate.actorName, true) &&
                        frame.text.contains("used ${candidate.moveName}", true)
                } ?: return@forEach
                observedFrames.putIfAbsent(expectedMove.slot, frame)
            }
            val observedTexts = observedFrames.values.map(ReplayFrameObservation::text)
            assertTrue(
                "$replayFileName skipped one or more opening move actors in the visible feed. " +
                    "native=$latestNativeLog; protocol=$latestProtocolLog; playback=$latestPlaybackState",
                expectedMovesObserved
            )
            assertTrue(
                "$replayFileName native Showdown did not emit all expected moves: $latestNativeLog",
                nativeMovesObserved
            )
            assertTrue(
                "$replayFileName native log did not synchronize with the visible move feed: $latestPlaybackState",
                nativeReplayCaughtUp
            )
            assertEquals(
                "$replayFileName native replay skipped one or more opening move actors. " +
                    "visible=$observedTexts; native=$latestNativeLog; protocol=$latestProtocolLog; " +
                    "playback=$latestPlaybackState",
                expectedMoves.mapTo(linkedSetOf(), MultiReplayMoveExpectation::slot),
                observedFrames.keys
            )
            observedFrames.forEach { (slot, frame) ->
                val expectedMove = expectedMoves.single { it.slot == slot }
                assertEquals(frame.text, frame.message.text)
                assertEquals(frame.message.sceneContext?.snapshot, frame.displayedScene)
                assertEquals(frame.message.sceneContext?.switchOutVisual, frame.displayedSwitchOutVisual)
                assertVisiblePokemonNamesBelongToDisplayedScene(frame)
                val snapshot = checkNotNull(frame.displayedScene)
                val playerCombatants = BattleFeedSceneState.combatantsForMessage(
                    snapshot.playerCombatants,
                    true,
                    frame.displayedSwitchOutVisual
                )
                val opponentCombatants = BattleFeedSceneState.combatantsForMessage(
                    snapshot.opponentCombatants,
                    false,
                    frame.displayedSwitchOutVisual
                )
                val activeCombatants = playerCombatants + opponentCombatants
                val combatant = activeCombatants.singleOrNull { it.slot.equals(slot, true) }
                assertEquals(frame.text, expectedMove.actorName, combatant?.name)
                assertEquals(frame.text, expectedMove.actorName, combatant?.species)
                val playerSide = playerCombatants.any { it.slot.equals(slot, true) }
                assertEquals(frame.text, expectedMove.side == BattleSpriteSide.PLAYER, playerSide)
                assertEquals(
                    frame.text,
                    expectedMove.expectedOpposingPrefix,
                    frame.text.startsWith("The opposing ", ignoreCase = true)
                )
                val spriteRequests = if (playerSide) frame.playerActiveSprites else frame.opponentActiveSprites
                val spriteRequest = spriteRequests[slot]
                assertEquals(frame.text, expectedMove.actorName, spriteRequest?.species)
                assertEquals(frame.text, expectedMove.side, spriteRequest?.side)
                val expectedPlayerSprites = BattleSpriteRequests.active(
                    playerCombatants,
                    BattleSpriteSide.PLAYER,
                    frame.spriteStyle
                ).associate { it.slot to it.request }
                val expectedOpponentSprites = BattleSpriteRequests.active(
                    opponentCombatants,
                    BattleSpriteSide.OPPONENT,
                    frame.spriteStyle
                ).associate { it.slot to it.request }
                assertEquals(frame.text, expectedPlayerSprites, frame.playerActiveSprites)
                assertEquals(frame.text, expectedOpponentSprites, frame.opponentActiveSprites)
            }
        } finally {
            scenario?.close()
            bitmap.recycle()
            preferences.edit().putBoolean("maintain_connection", previousConnectionPreference).commit()
        }
    }

    private fun assertVisiblePokemonNamesBelongToDisplayedScene(
        frame: ReplayFrameObservation,
        knownPokemonNames: Collection<String> = listOf("Gliscor", "Swanna", "Weavile", "Dewgong"),
        offScreenSources: Set<String> = emptySet()
    ) {
        val snapshot = frame.displayedScene ?: return
        val combatants = snapshot.playerCombatants + snapshot.opponentCombatants +
            listOfNotNull(frame.displayedSwitchOutVisual?.combatant)
        val identities = combatants.flatMap { listOf(it.name, it.species) }
            .filter(String::isNotBlank)
            .toSet()
        val moveSeparator = frame.text.indexOf(" used ", ignoreCase = true)
        val moveActor = frame.text
            .takeIf { moveSeparator >= 0 }
            ?.substring(0, moveSeparator)
            ?.trim()
            ?.trimStart('(', '[')
            ?.replaceFirst(Regex("^the opposing\\s+", RegexOption.IGNORE_CASE), "")
            ?.substringAfterLast("'s ")
            ?.trim()
        if (!moveActor.isNullOrBlank()) {
            assertTrue(
                "Visible replay entry '${frame.text}' names move actor $moveActor outside the displayed scene $identities",
                identities.any { it.equals(moveActor, ignoreCase = true) }
            )
        }
        knownPokemonNames
            .filter { identity -> frame.text.contains(identity, ignoreCase = true) }
            .forEach { identity ->
                if (identity in offScreenSources) return@forEach
                assertTrue(
                    "Visible replay entry '${frame.text}' names $identity outside the displayed scene $identities",
                    identities.any { it.equals(identity, ignoreCase = true) }
                )
            }
    }

    private fun drainDisplayedReplayMessages(
        activity: MainActivity,
        knownPokemonNames: Set<String>,
        packetByMessageId: Map<Long, List<String>>,
        observedFrames: MutableMap<Long, ReplayFrameObservation>,
        canvas: Canvas
    ) {
        val session = privateField(activity, "session") as BattleSession
        val scene = privateField(activity, "battleScene") as BattleSceneView
        val presentation = privateField(scene, "battleFeedPresentation") as BattleFeedPresentation
        var steps = 0
        val maximumSteps = session.battleFeedMessages(Int.MAX_VALUE).size + 2

        while (steps++ < maximumSteps) {
            scene.draw(canvas)
            val text = privateField(scene, "cachedBattleFeedVisibleText") as? String
            val message = privateField(presentation, "currentMessage") as? BattleFeedMessage
            if (text != null && message != null && message.text == text) {
                val frame = ReplayFrameObservation(
                    text = text,
                    message = message,
                    displayedScene = privateField(scene, "displayedBattleSceneSnapshot") as? BattleSession.BattleSceneSnapshot,
                    displayedSwitchOutVisual = privateField(scene, "displayedSwitchOutVisual") as? BattleSession.SwitchOutVisual,
                    spriteStyle = session.spriteStyle,
                    singlesBattle = session.isSinglesBattle(),
                    playerSprite = privateField(scene, "requestedPlayerSprite") as? BattleSpriteRequest,
                    opponentSprite = privateField(scene, "requestedOpponentSprite") as? BattleSpriteRequest,
                    playerActiveSprites = privateSpriteRequests(scene, "requestedPlayerActiveSprites"),
                    opponentActiveSprites = privateSpriteRequests(scene, "requestedOpponentActiveSprites")
                )
                assertEquals(frame.text, frame.message.text)
                assertEquals(frame.message.sceneContext?.snapshot, frame.displayedScene)
                assertEquals(frame.message.sceneContext?.switchOutVisual, frame.displayedSwitchOutVisual)
                assertVisiblePokemonNamesBelongToDisplayedScene(
                    frame,
                    knownPokemonNames,
                    protocolOffScreenPokemonNames(packetByMessageId[message.id].orEmpty())
                )
                frame.displayedScene?.let { snapshot ->
                    val switchOutVisual = frame.displayedSwitchOutVisual
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
                        style = frame.spriteStyle,
                        playerFallbackSpecies = session.playerPokemon,
                        opponentFallbackSpecies = session.opponentPokemon
                    )
                    if (spriteRequests.playerLead != null) {
                        assertEquals(frame.text, spriteRequests.playerLead.species, frame.playerSprite?.species)
                    }
                    if (spriteRequests.opponentLead != null) {
                        assertEquals(frame.text, spriteRequests.opponentLead.species, frame.opponentSprite?.species)
                    }
                }
                observedFrames.putIfAbsent(message.id, frame)
            }

            val pendingMessages = privateField(presentation, "pendingMessages") as? Collection<*>
            if (pendingMessages.isNullOrEmpty() || !session.battleFeedVisible) return
            val nowMillis = SystemClock.elapsedRealtime()
            presentation.advanceOnTap(nowMillis)
            presentation.advanceOnTap(nowMillis)
        }
    }

    private fun protocolOffScreenPokemonNames(lines: List<String>): Set<String> {
        val sourcePattern = Regex("^\\[(?:wisher|of)]\\s+(.+)$", RegexOption.IGNORE_CASE)
        val actorPattern = Regex("^p[1-4][a-z]?:\\s*(.+)$", RegexOption.IGNORE_CASE)
        return buildSet {
            lines.forEach { line ->
                val fields = line.split('|')
                if (fields.getOrNull(1) == "-heal" &&
                    fields.getOrNull(4)?.equals("[from] move: Revival Blessing", true) == true
                ) {
                    fields.getOrNull(2)
                        ?.let { actorPattern.matchEntire(it)?.groupValues?.get(1) }
                        ?.substringBefore(',')
                        ?.trim()
                        ?.takeIf { it.length >= 3 }
                        ?.let(::add)
                }
                fields.drop(2).forEach { field ->
                    sourcePattern.matchEntire(field)?.groupValues?.get(1)
                        ?.let { source -> actorPattern.matchEntire(source)?.groupValues?.get(1) ?: source }
                        ?.substringBefore(',')
                        ?.trim()
                        ?.takeIf { it.length >= 3 }
                        ?.let(::add)
                }
            }
        }
    }

    private fun privateField(target: Any, name: String): Any? = target.javaClass
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(target)

    private fun setPrivateField(target: Any, name: String, value: Any?) {
        target.javaClass.getDeclaredField(name)
            .apply { isAccessible = true }
            .set(target, value)
    }

    private fun privateSpriteRequests(target: Any, name: String): Map<String, BattleSpriteRequest> =
        ((privateField(target, name) as? Map<*, *>) ?: emptyMap<Any, Any>())
            .mapNotNull { (slot, request) ->
                if (slot is String && request is BattleSpriteRequest) slot to request else null
            }
            .toMap()
}
