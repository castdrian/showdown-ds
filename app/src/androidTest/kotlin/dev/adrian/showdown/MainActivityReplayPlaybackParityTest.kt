package dev.adrian.showdown

import android.content.ContextWrapper
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
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class MainActivityReplayPlaybackParityTest {
    private companion object {
        const val REPLAY_FRAME_CAPTURE_WIDTH = 480
        const val REPLAY_FRAME_CAPTURE_HEIGHT = 270
    }

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
        val opponentActiveSprites: Map<String, BattleSpriteRequest> = emptyMap(),
        val playerSpriteAssetPath: String? = null,
        val opponentSpriteAssetPath: String? = null,
        val playerActiveSpriteAssetPaths: Map<String, String> = emptyMap(),
        val opponentActiveSpriteAssetPaths: Map<String, String> = emptyMap()
    )

    private data class MultiReplayMoveExpectation(
        val slot: String,
        val actorName: String,
        val moveName: String,
        val side: BattleSpriteSide,
        val expectedOpposingPrefix: Boolean
    )

    private data class NativeReplayMoveIdentity(
        val sourceLine: String,
        val slot: String,
        val name: String,
        val moveName: String,
        val species: String?,
        val side: BattleSpriteSide?
    )

    @Test
    fun officialDewgongBackAnimationDecodesOnDevice() {
        val (cache, cacheDirectory) = isolatedSpriteCache()
        val completed = CountDownLatch(1)
        var decodedAsset: ShowdownSpriteCache.SpriteAsset? = null
        val requestSprite = ShowdownSpriteCache::class.java.declaredMethods.single {
            it.name == "requestSprite" && it.parameterTypes.size == 2
        }.apply { isAccessible = true }
        val receiver: (ShowdownSpriteCache.SpriteAsset?) -> Unit = { asset ->
            decodedAsset = asset
            completed.countDown()
        }

        try {
            requestSprite.invoke(cache, "sprites/ani-back/dewgong.gif", receiver)
            assertTrue("The direct animated Dewgong back-sprite request timed out", completed.await(30, TimeUnit.SECONDS))
            assertNotNull("The device sprite decoder rejected the official animated Dewgong back sprite", decodedAsset)
            assertTrue("The official Dewgong back sprite was not animated", decodedAsset?.isAnimated == true)
            assertEquals("sprites/ani-back/dewgong.gif", decodedAsset?.resolvedAssetPath)
        } finally {
            cache.close()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun modernDewgongBackSpriteResolvesAlongsideOtherBattleActors() {
        val (cache, cacheDirectory) = isolatedSpriteCache()
        val completed = CountDownLatch(1)
        val request = BattleSpriteRequest.forPlayer("Dewgong", BattleSession.SpriteStyle.MODERN_3D)
        var resolvedAsset: ShowdownSpriteCache.SpriteAsset? = null
        val battleRequests = listOf(
            BattleSpriteRequest.forPlayer("Gliscor", BattleSession.SpriteStyle.MODERN_3D),
            BattleSpriteRequest.forOpponent("Swanna", BattleSession.SpriteStyle.MODERN_3D),
            BattleSpriteRequest.forOpponent("Weavile", BattleSession.SpriteStyle.MODERN_3D),
            request
        )

        try {
            battleRequests.forEach { battleRequest ->
                cache.requestPokemon(battleRequest) { asset ->
                    if (battleRequest == request) {
                        resolvedAsset = asset
                        completed.countDown()
                    }
                }
            }
            val completedWithinTime = completed.await(30, TimeUnit.SECONDS)
            val pendingFiles = (privateField(cache, "pendingFileReceivers") as Map<*, *>).keys
            assertTrue("The modern Dewgong sprite request timed out; pending files: $pendingFiles", completedWithinTime)
            assertNotNull("The modern Dewgong battle request did not resolve a back sprite", resolvedAsset)
            assertTrue("The modern Dewgong battle request resolved a non-animated asset", resolvedAsset?.isAnimated == true)
            assertTrue(
                "The modern Dewgong battle request resolved an unplanned asset: ${resolvedAsset?.resolvedAssetPath}",
                resolvedAsset?.resolvedAssetPath in ShowdownAssetPaths.battleSpriteResolutionPlan(request).allCandidates
            )
        } finally {
            cache.close()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun modernCorviknightBackSpritePrefersAvailableHdAnimation() {
        val (cache, cacheDirectory) = isolatedSpriteCache()
        val hdSpriteSelected = CountDownLatch(1)
        val request = BattleSpriteRequest.forPlayer("Corviknight", BattleSession.SpriteStyle.MODERN_3D)
        var resolvedAsset: ShowdownSpriteCache.SpriteAsset? = null

        try {
            cache.requestPokemon(request) { asset ->
                resolvedAsset = asset
                if (asset?.resolvedAssetPath?.contains("/animados-gigante/") == true) {
                    hdSpriteSelected.countDown()
                }
            }
            assertTrue("The HD Corviknight back-sprite request timed out", hdSpriteSelected.await(30, TimeUnit.SECONDS))
            assertNotNull("The modern Corviknight battle request did not resolve a back sprite", resolvedAsset)
            assertTrue("The modern Corviknight battle request resolved a non-animated asset", resolvedAsset?.isAnimated == true)
            assertTrue(
                "The available HD Corviknight back sprite was not selected: ${resolvedAsset?.resolvedAssetPath}",
                resolvedAsset?.resolvedAssetPath?.contains("/animados-gigante/") == true
            )
        } finally {
            cache.close()
            cacheDirectory.deleteRecursively()
        }
    }

    @Test
    fun productionReplayKeepsEachVisibleLogEntryPairedWithItsDisplayedPokemon() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        clearCachedSpriteResolutionPlan(
            targetContext,
            BattleSpriteRequest.forPlayer("Dewgong", BattleSession.SpriteStyle.MODERN_3D)
        )
        val replayJson = InstrumentationRegistry.getInstrumentation().context.assets
            .open("gen9randombattle-2691989691.json")
            .bufferedReader()
            .use { it.readText() }
        val completeReplay = ShowdownReplayImporter.payload(replayJson)
        val replayLines = completeReplay.log.lines()
        val finalReplayLine = replayLines.indexOfFirst { it.startsWith("|-hitcount|p1a: Dewgong|3") }
        assertTrue("The official replay fixture is missing its expected multi-hit event", finalReplayLine >= 0)
        val replayLinesToPlay = replayLines.take(finalReplayLine + 1)
        val replay = completeReplay.copy(log = replayLinesToPlay.joinToString("\n"))
        val knownPokemonNames = replayPokemonNames(replayLinesToPlay)
        val preferences = targetContext.getSharedPreferences("showdown_live", 0)
        val previousConnectionPreference = preferences.getBoolean("maintain_connection", false)
        val observedFrames = linkedMapOf<Long, ReplayFrameObservation>()
        val bitmap = Bitmap.createBitmap(REPLAY_FRAME_CAPTURE_WIDTH, REPLAY_FRAME_CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var latestNativeLog = emptyList<String>()
        var latestProtocolLog = emptyList<String>()
        var latestNativeGeneration = -1L
        var latestProtocolGeneration = -1L
        var latestPlaybackState = ""
        var targetVisibleAt: Long? = null
        var targetNativeCaughtUp = false
        var targetSpriteAssetsLoaded = false
        var targetSpriteDiagnostics = "unobserved"
        val nativeMoveIdentitiesByMessageId = linkedMapOf<Long, NativeReplayMoveIdentity>()
        val playbackTimeline = mutableListOf<String>()
        val deadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(90)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            preferences.edit().putBoolean("maintain_connection", false).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val activeScenario = checkNotNull(scenario)
            activeScenario.onActivity { activity ->
                val session = privateField(activity, "session") as BattleSession
                observeProtocolMoveIdentities(session, nativeMoveIdentitiesByMessageId)
                setPrivateField(activity, "restoredReplaySpeed", BattlePlaybackSpeed.MAXIMUM)
                setPrivateField(activity, "lightweightBattlePlayback", false)
                MainActivity::class.java.getDeclaredMethod("showReplay", ShowdownReplayPayload::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, replay)
                val effectsView = privateField(activity, "showdownMoveEffects") as? ShowdownMoveEffectsView
                assertNotNull("The replay did not create the native Showdown renderer", effectsView)
                checkNotNull(effectsView).setAnimationsDisabledForTesting(true)
            }

            while (SystemClock.elapsedRealtime() < deadline &&
                (targetVisibleAt == null || ((!targetNativeCaughtUp || !targetSpriteAssetsLoaded) &&
                    SystemClock.elapsedRealtime() - checkNotNull(targetVisibleAt) < TimeUnit.SECONDS.toMillis(45)))
            ) {
                activeScenario.onActivity { activity ->
                    val session = privateField(activity, "session") as BattleSession
                    val scene = privateField(activity, "battleScene") as BattleSceneView
                    val feedPresentation = checkNotNull(privateField(scene, "battleFeedPresentation"))
                    latestNativeLog = session.showdownBattleLog()
                    latestProtocolLog = session.battleLog()
                    latestNativeGeneration = privateField(session, "nativeBattleLogGeneration") as Long
                    latestProtocolGeneration = session.battleLogGeneration()
                    observeDisplayedReplayMessage(
                        activity,
                        knownPokemonNames,
                        observedFrames,
                        canvas
                    )
                    val frame = observedFrames.values.lastOrNull() ?: return@onActivity
                    val message = privateField(feedPresentation, "currentMessage") as? BattleFeedMessage ?: return@onActivity
                    if (message.id != frame.message.id || message.text != frame.text) return@onActivity
                    val text = frame.text
                    if (text.contains("Triple Axel", true) && text.contains("Weavile", true) && targetVisibleAt == null) {
                        targetVisibleAt = SystemClock.elapsedRealtime()
                    }
                    if (
                        targetVisibleAt != null &&
                        frame.playerSprite?.species.equals("Dewgong", true) &&
                        frame.opponentSprite?.species.equals("Weavile", true)
                    ) {
                        targetSpriteAssetsLoaded = frame.playerSpriteAssetPath != null && frame.opponentSpriteAssetPath != null
                        val spriteCache = checkNotNull(privateField(scene, "spriteCache"))
                        val pendingFiles = (privateField(spriteCache, "pendingFileReceivers") as Map<*, *>).keys
                        val pendingSprites = (privateField(spriteCache, "pendingSpriteReceivers") as Map<*, *>).keys
                        targetSpriteDiagnostics = "playerLoaded=${privateField(scene, "playerSprite") != null}; " +
                            "playerAsset=${frame.playerSpriteAssetPath}; opponentLoaded=${privateField(scene, "opponentSprite") != null}; " +
                            "pendingFiles=$pendingFiles; pendingSprites=$pendingSprites; " +
                            "opponentAsset=${frame.opponentSpriteAssetPath}; playerRequest=${frame.playerSprite}; " +
                            "opponentRequest=${frame.opponentSprite}"
                    }
                    targetNativeCaughtUp = latestNativeGeneration == latestProtocolGeneration &&
                        latestNativeLog.any { it.contains("Triple Axel", true) && it.contains("Weavile", true) }
                    val pendingMessages = privateField(feedPresentation, "pendingMessages") as? Collection<*>
                    val pendingPackets = privateField(activity, "pendingBattlePackets") as? Collection<*>
                    val state = "visible=$text#${frame.message.id}; protocolGen=$latestProtocolGeneration; " +
                        "nativeGen=$latestNativeGeneration; nativeTail=${latestNativeLog.takeLast(3)}; " +
                        "protocolTail=${latestProtocolLog.takeLast(3)}; feedPending=${pendingMessages?.size}; " +
                        "packetPending=${pendingPackets?.size}; scheduled=${privateField(activity, "battlePacketPlaybackScheduled")}; " +
                        "barrier=${privateField(privateField(activity, "battlePlaybackBarrier")!!, "awaitedToken")}; " +
                        "stalled=${privateField(activity, "rendererRecoveryStalled")}; lightweight=${privateField(activity, "lightweightBattlePlayback")}"
                    if (playbackTimeline.lastOrNull() != state) playbackTimeline.add(state)
                    latestPlaybackState = state
                }
                Thread.sleep(250L)
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
            val visibleMoveFrames = observedFrames.filterValues { it.text.contains(" used ", true) }
            assertTrue("The production replay did not display its multi-hit move entry: $observedTexts", visibleMoveFrames.isNotEmpty())
            visibleMoveFrames.forEach { (messageId, frame) ->
                val identity = checkNotNull(nativeMoveIdentitiesByMessageId[messageId]) {
                    "The visible native move ${frame.text} had no matching protocol move identity"
                }
                assertEquals(identity.sourceLine, identity.name, moveActorFromNativeMessage(frame.text))
                assertTrue(identity.sourceLine, frame.text.contains(identity.moveName, true))
                val displayedCombatants = checkNotNull(frame.displayedScene).let { snapshot ->
                    snapshot.playerCombatants + snapshot.opponentCombatants
                }
                val combatant = displayedCombatants.singleOrNull { it.slot.equals(identity.slot, true) }
                assertNotNull("${identity.sourceLine} has no displayed combatant in ${frame.text}", combatant)
                assertEquals(identity.sourceLine, identity.name, combatant?.name)
                assertEquals(identity.sourceLine, identity.species, combatant?.species)
                val sprite = if (identity.side == BattleSpriteSide.PLAYER) frame.playerSprite else frame.opponentSprite
                assertEquals(identity.sourceLine, identity.species, sprite?.species)
                assertEquals(identity.sourceLine, identity.side, sprite?.side)
            }
            assertTrue(
                "The production replay did not draw the active Pokémon assets for its visible multi-hit action: $observedTexts; $targetSpriteDiagnostics",
                targetSpriteAssetsLoaded
            )
            observedFrames.values.forEach { frame ->
                assertEquals(frame.text, frame.message.text)
                assertEquals(frame.message.sceneContext?.snapshot, frame.displayedScene)
                assertEquals(frame.message.sceneContext?.switchOutVisual, frame.displayedSwitchOutVisual)
                assertVisiblePokemonNamesBelongToDisplayedScene(frame, knownPokemonNames)
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
            bitmap.recycle()
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
    fun minimalDoublesOpeningKeepsNativeMoveActorsPairedWithVisibleSprites() {
        assertNativeProductionReplayMovesPairedWithVisibleSprites(
            replayFileName = "gen9doublesou-2691960998.json",
            finalMoveLinePrefix = "|move|p1b: Blaziken|Aura Sphere|p2b: Porygon2",
            expectedMoves = listOf(
                MultiReplayMoveExpectation("p1a", "Delibird", "Fake Out", BattleSpriteSide.PLAYER, false),
                MultiReplayMoveExpectation("p1b", "Blaziken", "Aura Sphere", BattleSpriteSide.PLAYER, false)
            ),
            minimalDoublesOpening = true
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
        expectedMoves: List<MultiReplayMoveExpectation>,
        minimalDoublesOpening: Boolean = false
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
        val replayLog = replayLines.take(finalReplayLine + 1).let { lines ->
            if (!minimalDoublesOpening) {
                lines
            } else {
                lines.filter { line ->
                    line.startsWith("|player|") ||
                        line.startsWith("|gametype|") ||
                        line.startsWith("|gen|") ||
                        line.startsWith("|tier|") ||
                        line == "|start" ||
                        line.startsWith("|switch|") ||
                        line.startsWith("|-ability|p2b: Porygon2|Download") ||
                        line.startsWith("|-boost|p2b: Porygon2|atk") ||
                        line == "|turn|1" ||
                        line.startsWith("|move|p1a: Delibird|Fake Out") ||
                        line.startsWith("|-damage|p2b: Porygon2|") ||
                        line.startsWith("|move|p1b: Blaziken|Aura Sphere") ||
                        line.startsWith("|-supereffective|p2b: Porygon2") ||
                        line.startsWith("|cant|p2b: Porygon2|flinch")
                }
            }
        }
        val replay = completeReplay.copy(log = replayLog.joinToString("\n"))
        val preferences = targetContext.getSharedPreferences("showdown_live", 0)
        val previousConnectionPreference = preferences.getBoolean("maintain_connection", false)
        val observedFrames = linkedMapOf<String, ReplayFrameObservation>()
        val displayedFrames = linkedMapOf<Long, ReplayFrameObservation>()
        val bitmap = Bitmap.createBitmap(REPLAY_FRAME_CAPTURE_WIDTH, REPLAY_FRAME_CAPTURE_HEIGHT, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        var latestNativeLog = emptyList<String>()
        var latestProtocolLog = emptyList<String>()
        var latestFeedMessages = emptyList<BattleFeedMessage>()
        var latestPlaybackState = "unobserved"
        var expectedMovesObserved = false
        var nativeMovesObserved = false
        var nativeReplayCaughtUp = false
        val feedTimeline = mutableListOf<String>()
        val feedInputTimeline = mutableListOf<String>()
        var lastFeedInputState = ""
        var lastFeedQueueState = ""
        var lastProgressState = ""
        var lastProgressAt = SystemClock.elapsedRealtime()
        var playbackStalled = false
        val playbackDeadline = SystemClock.elapsedRealtime() + TimeUnit.SECONDS.toMillis(180)
        var scenario: ActivityScenario<MainActivity>? = null

        try {
            preferences.edit().putBoolean("maintain_connection", false).commit()
            scenario = ActivityScenario.launch(MainActivity::class.java)
            val activeScenario = checkNotNull(scenario)
            activeScenario.onActivity { activity ->
                setPrivateField(activity, "restoredReplaySpeed", 1f)
                setPrivateField(activity, "lightweightBattlePlayback", false)
                MainActivity::class.java.getDeclaredMethod("showReplay", ShowdownReplayPayload::class.java)
                    .apply { isAccessible = true }
                    .invoke(activity, replay)
                val effectsView = privateField(activity, "showdownMoveEffects") as? ShowdownMoveEffectsView
                assertNotNull("The native Showdown renderer was not created for $replayFileName", effectsView)
                checkNotNull(effectsView).setAnimationsDisabledForTesting(true)
            }

            while (SystemClock.elapsedRealtime() < playbackDeadline &&
                (!expectedMovesObserved || !nativeMovesObserved || !nativeReplayCaughtUp) && !playbackStalled
            ) {
                activeScenario.onActivity { activity ->
                    val session = privateField(activity, "session") as BattleSession
                    val scene = privateField(activity, "battleScene") as BattleSceneView
                    val feedPresentation = checkNotNull(privateField(scene, "battleFeedPresentation"))
                    latestNativeLog = session.showdownBattleLog()
                    latestProtocolLog = session.battleLog()
                    latestFeedMessages = session.battleFeedMessages()
                    val nativeGeneration = privateField(session, "nativeBattleLogGeneration") as Long
                    val feedInputState = "$nativeGeneration/${session.battleLogGeneration()}:" +
                        latestFeedMessages.map { it.id to it.text }
                    if (feedInputState != lastFeedInputState) {
                        feedInputTimeline += "${SystemClock.elapsedRealtime()}:$feedInputState"
                        lastFeedInputState = feedInputState
                    }
                    observeDisplayedReplayMessage(
                        activity,
                        expectedMoves.mapTo(linkedSetOf(), MultiReplayMoveExpectation::actorName),
                        displayedFrames,
                        canvas
                    )
                    val pendingPackets = privateField(activity, "pendingBattlePackets") as? Collection<*>
                    val scheduled = privateField(activity, "battlePacketPlaybackScheduled") as Boolean
                    val currentFeedMessage = privateField(feedPresentation, "currentMessage") as? BattleFeedMessage
                    val pendingFeedMessages = privateField(feedPresentation, "pendingMessages") as? Collection<*>
                    val feedQueueState = "${currentFeedMessage?.id}:${currentFeedMessage?.text}; " +
                        "pending=${pendingFeedMessages?.map { (it as? BattleFeedMessage)?.id to (it as? BattleFeedMessage)?.text }}"
                    if (feedQueueState != lastFeedQueueState) {
                        feedTimeline += "${SystemClock.elapsedRealtime()}:$feedQueueState"
                        lastFeedQueueState = feedQueueState
                    }
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
                        "feedCurrent=${currentFeedMessage?.id}:${currentFeedMessage?.text}; " +
                        "feedPending=${pendingFeedMessages?.map { (it as? BattleFeedMessage)?.text }}; " +
                        "visible=${privateField(scene, "cachedBattleFeedVisibleText")}; " +
                        "observed=${displayedFrames.size}; moveSlots=$observedMoveSlots; " +
                        "nativeTail=${latestNativeLog.takeLast(3)}"
                    val now = SystemClock.elapsedRealtime()
                    if (latestPlaybackState != lastProgressState) {
                        lastProgressState = latestPlaybackState
                        lastProgressAt = now
                    }
                    val playbackReadyAndIdle =
                        privateField(effectsView, "javascriptReady") as Boolean &&
                        nativeReplayCaughtUp &&
                        pendingPackets?.isEmpty() == true &&
                        !scheduled &&
                        activeBarrierToken == null &&
                        currentFeedMessage == null && pendingFeedMessages?.isEmpty() == true
                    val stallWindowMillis = if (playbackReadyAndIdle) {
                        TimeUnit.SECONDS.toMillis(4)
                    } else {
                        TimeUnit.SECONDS.toMillis(40)
                    }
                    playbackStalled = now - lastProgressAt >= stallWindowMillis
                }
                Thread.sleep(250L)
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
                    "native=$latestNativeLog; protocol=$latestProtocolLog; displayed=${displayedFrames.values.map { it.message.id to it.text }}; " +
                    "feed=${latestFeedMessages.map { it.id to it.text }}; inputs=$feedInputTimeline; " +
                    "queue=$feedTimeline; playback=$latestPlaybackState",
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

    private fun replayPokemonNames(lines: List<String>): Set<String> = buildSet {
        val actorPattern = Regex("^p[1-4][a-z]:\\s*(.+)$", RegexOption.IGNORE_CASE)
        val speciesActions = setOf("switch", "drag", "replace", "detailschange", "-formechange")
        lines.forEach { line ->
            val fields = line.split('|')
            fields.drop(2).forEach { field ->
                actorPattern.matchEntire(field)
                    ?.groupValues
                    ?.get(1)
                    ?.substringBefore(',')
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
            if (fields.getOrNull(1) in speciesActions) {
                fields.getOrNull(3)
                    ?.substringBefore(',')
                    ?.trim()
                    ?.takeIf(String::isNotBlank)
                    ?.let(::add)
            }
        }
    }

    private fun observeDisplayedReplayMessage(
        activity: MainActivity,
        knownPokemonNames: Set<String>,
        observedFrames: MutableMap<Long, ReplayFrameObservation>,
        canvas: Canvas
    ) {
        val session = privateField(activity, "session") as BattleSession
        val scene = privateField(activity, "battleScene") as BattleSceneView
        val presentation = privateField(scene, "battleFeedPresentation") as BattleFeedPresentation
        if (scene.width <= 0 || scene.height <= 0) return
        val captureBounds = canvas.clipBounds
        canvas.save()
        canvas.scale(
            captureBounds.width().toFloat() / scene.width,
            captureBounds.height().toFloat() / scene.height
        )
        scene.draw(canvas)
        canvas.restore()
        val text = privateField(scene, "cachedBattleFeedVisibleText") as? String
        val message = privateField(presentation, "currentMessage") as? BattleFeedMessage
        if (text == null || message == null || message.text != text) return
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
            opponentActiveSprites = privateSpriteRequests(scene, "requestedOpponentActiveSprites"),
            playerSpriteAssetPath = (privateField(scene, "playerSprite") as? ShowdownSpriteCache.SpriteAsset)?.resolvedAssetPath,
            opponentSpriteAssetPath = (privateField(scene, "opponentSprite") as? ShowdownSpriteCache.SpriteAsset)?.resolvedAssetPath,
            playerActiveSpriteAssetPaths = privateSpriteAssetPaths(scene, "playerActiveSprites"),
            opponentActiveSpriteAssetPaths = privateSpriteAssetPaths(scene, "opponentActiveSprites")
        )
        assertEquals(frame.text, frame.message.text)
        assertEquals(frame.message.sceneContext?.snapshot, frame.displayedScene)
        assertEquals(frame.message.sceneContext?.switchOutVisual, frame.displayedSwitchOutVisual)
        assertVisiblePokemonNamesBelongToDisplayedScene(frame, knownPokemonNames)
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
            assertRenderedSpriteAssetMatchesRequest(
                frame.text,
                "player",
                spriteRequests.playerLead,
                frame.playerSpriteAssetPath
            )
            assertRenderedSpriteAssetMatchesRequest(
                frame.text,
                "opponent",
                spriteRequests.opponentLead,
                frame.opponentSpriteAssetPath
            )
            spriteRequests.playerActive.forEach { request ->
                assertRenderedSpriteAssetMatchesRequest(
                    frame.text,
                    "player slot ${request.slot}",
                    request.request,
                    frame.playerActiveSpriteAssetPaths[request.slot]
                )
            }
            spriteRequests.opponentActive.forEach { request ->
                assertRenderedSpriteAssetMatchesRequest(
                    frame.text,
                    "opponent slot ${request.slot}",
                    request.request,
                    frame.opponentActiveSpriteAssetPaths[request.slot]
                )
            }
        }
        observedFrames[message.id] = frame
    }

    private fun assertRenderedSpriteAssetMatchesRequest(
        text: String,
        side: String,
        request: BattleSpriteRequest?,
        resolvedAssetPath: String?
    ) {
        if (request == null || resolvedAssetPath == null) return
        assertTrue(
            "$side sprite drawn for '$text' came from a path unrelated to ${request.species}: $resolvedAssetPath",
            resolvedAssetPath in ShowdownAssetPaths.battleSpriteResolutionPlan(request).allCandidates
        )
    }

    private fun observeProtocolMoveIdentities(
        session: BattleSession,
        identitiesByMessageId: MutableMap<Long, NativeReplayMoveIdentity>
    ) {
        session.addProtocolListener { lines, identitiesByLine ->
            lines.forEachIndexed { index, line ->
                val fields = line.split('|')
                if (fields.getOrNull(1) != "move") return@forEachIndexed
                val actorField = fields.getOrNull(2).orEmpty()
                val slot = actorField.substringBefore(':').trim()
                val name = actorField.substringAfter(':', "").substringBefore(',').trim()
                val moveName = fields.getOrNull(3).orEmpty()
                if (slot.isBlank() || name.isBlank() || moveName.isBlank()) return@forEachIndexed
                val identity = identitiesByLine.getOrNull(index) ?: return@forEachIndexed
                identity.messageIds.forEach { messageId ->
                    val snapshot = session.battleSceneSnapshotForFeedMessage(messageId)
                    val playerCombatant = snapshot?.playerCombatants?.singleOrNull { it.slot.equals(slot, true) }
                    val opponentCombatant = snapshot?.opponentCombatants?.singleOrNull { it.slot.equals(slot, true) }
                    val combatant = playerCombatant ?: opponentCombatant
                    identitiesByMessageId[messageId] = NativeReplayMoveIdentity(
                        sourceLine = line,
                        slot = slot,
                        name = name,
                        moveName = moveName,
                        species = combatant?.species,
                        side = when {
                            playerCombatant != null -> BattleSpriteSide.PLAYER
                            opponentCombatant != null -> BattleSpriteSide.OPPONENT
                            else -> null
                        }
                    )
                }
            }
        }
    }

    private fun moveActorFromNativeMessage(text: String): String {
        val moveSeparator = text.indexOf(" used ", ignoreCase = true)
        if (moveSeparator < 0) return ""
        return text.substring(0, moveSeparator)
            .trim()
            .trimStart('(', '[')
            .replaceFirst(Regex("^the opposing\\s+", RegexOption.IGNORE_CASE), "")
            .substringAfterLast("'s ")
            .trim()
    }

    private fun privateField(target: Any, name: String): Any? = target.javaClass
        .getDeclaredField(name)
        .apply { isAccessible = true }
        .get(target)

    private fun clearCachedSpriteResolutionPlan(context: android.content.Context, request: BattleSpriteRequest) {
        val diskCache = File(context.cacheDir, "showdown-resources")
        val digest = MessageDigest.getInstance("SHA-256")
        ShowdownAssetPaths.battleSpriteResolutionPlan(request).allCandidates.forEach { path ->
            val filename = digest.digest(path.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it.toInt() and 0xff) } + ".${showdownCacheExtension(path)}"
            File(diskCache, filename).delete()
        }
    }

    private fun isolatedSpriteCache(): Pair<ShowdownSpriteCache, File> {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val cacheDirectory = File(targetContext.cacheDir, "sprite-test-${SystemClock.elapsedRealtimeNanos()}")
        assertTrue("Could not create an isolated sprite cache", cacheDirectory.mkdirs())
        val cacheContext = object : ContextWrapper(targetContext) {
            override fun getCacheDir(): File = cacheDirectory
        }
        return ShowdownSpriteCache(cacheContext) to cacheDirectory
    }

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

    private fun privateSpriteAssetPaths(target: Any, name: String): Map<String, String> =
        ((privateField(target, name) as? Map<*, *>) ?: emptyMap<Any, Any>())
            .mapNotNull { (slot, asset) ->
                val path = (asset as? ShowdownSpriteCache.SpriteAsset)?.resolvedAssetPath
                if (slot is String && path != null) slot to path else null
            }
            .toMap()

}
