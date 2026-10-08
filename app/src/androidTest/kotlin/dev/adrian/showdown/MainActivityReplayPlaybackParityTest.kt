package dev.adrian.showdown

import android.os.SystemClock
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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
        val opponentSprite: BattleSpriteRequest?
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

            while (SystemClock.elapsedRealtime() < deadline && observedFrames.values.none {
                    it.text.contains("Triple Axel", true) && it.text.contains("Weavile", true)
                }) {
                activeScenario.onActivity { activity ->
                    val session = privateField(activity, "session") as BattleSession
                    val scene = privateField(activity, "battleScene") as BattleSceneView
                    val feedPresentation = checkNotNull(privateField(scene, "battleFeedPresentation"))
                    latestNativeLog = session.showdownBattleLog()
                    latestProtocolLog = session.battleLog()
                    scene.invalidate()
                    val text = privateField(scene, "cachedBattleFeedVisibleText") as? String ?: return@onActivity
                    val message = privateField(feedPresentation, "currentMessage") as? BattleFeedMessage ?: return@onActivity
                    if (message.text != text) return@onActivity
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
                    "visible=$observedTexts; native=$latestNativeLog; protocol=$latestProtocolLog",
                observedTexts.any { it.contains("Triple Axel", true) && it.contains("Weavile", true) }
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

    private fun assertVisiblePokemonNamesBelongToDisplayedScene(frame: ReplayFrameObservation) {
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
            ?.replaceFirst(Regex("^the opposing\\s+", RegexOption.IGNORE_CASE), "")
            ?.substringAfterLast("'s ")
            ?.trim()
        if (!moveActor.isNullOrBlank()) {
            assertTrue(
                "Visible replay entry '${frame.text}' names move actor $moveActor outside the displayed scene $identities",
                identities.any { it.equals(moveActor, ignoreCase = true) }
            )
        }
        listOf("Gliscor", "Swanna", "Weavile", "Dewgong")
            .filter { identity -> frame.text.contains(identity, ignoreCase = true) }
            .forEach { identity ->
                assertTrue(
                    "Visible replay entry '${frame.text}' names $identity outside the displayed scene $identities",
                    identities.any { it.equals(identity, ignoreCase = true) }
                )
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
}
