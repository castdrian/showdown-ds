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
        val playbackLines = listOf("|init|battle") + lines.take(whiteHerbLine + 1)
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

        BattlePlaybackTiming.chunks(playbackLines).forEach { packet ->
            val entryCount = activity.nativeEntries.size
            val syncCount = activity.synchronizedGenerations.size
            session.applyProtocolPacket(packet)
            val generation = session.battleLogGeneration()
            activity.renderer.applyProtocol(packet, generation)
            awaitSynchronization(activity, syncCount)
            val nativeEntries = activity.nativeEntries.drop(entryCount)
                .filter { it.first == generation }
                .map { it.second }
            if (nativeEntries.isNotEmpty()) {
                session.appendShowdownBattleLog(nativeEntries.joinToString("<br />"), generation)
            }
            session.markNativeBattleLogSynchronized(generation)
            val nativeTexts = nativeEntries.flatMap(ShowdownBattleLogFilter::visibleEntries)

            if (packet.any { it.startsWith("|-heal|p2a: Salazzle|55/249") }) {
                assertTrue("Upstream Showdown did not render its known Leftovers narration: $nativeTexts", expectedLeftoversText in nativeTexts)
                assertActorSnapshot(session, expectedLeftoversText, "p2a", "Salazzle", "Salazzle", false)
                sawLeftovers = true
            }
            if (packet.any { it.startsWith("|-start|p2a: Salazzle|Encore") }) {
                assertTrue("Upstream Showdown did not render its Encore narration: $nativeTexts", nativeTexts.any { it.contains("Salazzle", true) && it.contains("encore", true) })
                val encoreText = nativeTexts.last { it.contains("Salazzle", true) && it.contains("encore", true) }
                assertActorSnapshot(session, encoreText, "p2a", "Salazzle", "Salazzle", false)
                sawEncore = true
            }
            if (packet.any { it.startsWith("|-enditem|p1a: Minior|White Herb") }) {
                assertTrue("Upstream Showdown did not render its known White Herb narration: $nativeTexts", expectedWhiteHerbText in nativeTexts)
                assertActorSnapshot(session, expectedWhiteHerbText, "p1a", "Minior", "Minior-Meteor", true)
                sawWhiteHerb = true
            }
        }

        assertTrue("The real renderer never emitted the Leftovers event", sawLeftovers)
        assertTrue("The real renderer never emitted the Encore event", sawEncore)
        assertTrue("The real renderer never emitted the White Herb event", sawWhiteHerb)
    }

    private fun awaitSynchronization(activity: ShowdownLogParityHarnessActivity, previousCount: Int) {
        val deadline = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(2)
        while (activity.synchronizedGenerations.size <= previousCount && System.currentTimeMillis() < deadline) {
            Thread.sleep(25L)
        }
        assertTrue(
            "The upstream renderer did not finish the queued protocol packet",
            activity.synchronizedGenerations.size > previousCount
        )
    }

    private fun assertActorSnapshot(
        session: BattleSession,
        text: String,
        slot: String,
        name: String,
        species: String,
        playerSide: Boolean
    ) {
        val message = session.battleFeedMessages().lastOrNull { it.text == text }
        assertNotNull("The actual Showdown line was not associated with a battle-feed entry: $text", message)
        val snapshot = session.battleSceneSnapshotForFeedMessage(message!!.id)
        assertNotNull("The actual Showdown line lost its protocol scene snapshot: $text", snapshot)
        val combatants = if (playerSide) snapshot!!.playerCombatants else snapshot!!.opponentCombatants
        val combatant = combatants.singleOrNull { it.slot.equals(slot, true) }
        assertNotNull("The actual Showdown line points at the wrong side or slot: $text", combatant)
        assertEquals(text, name, combatant!!.name)
        assertEquals(text, species, combatant.species)
    }
}
