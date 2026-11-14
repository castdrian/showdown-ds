package dev.adrian.showdown

import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Checks that the Showdown client WebView exists and loads; what it renders is Showdown's own code. */
@RunWith(AndroidJUnit4::class)
class ShowdownBattleSceneViewTest {
    private val pageLoadTimeoutMillis = TimeUnit.SECONDS.toMillis(30)
    private val minimalBattle = listOf(
        "|init|battle",
        "|player|p1|PLAYER||",
        "|player|p2|OPPONENT||",
        "|gametype|singles",
        "|start"
    )

    @get:Rule
    val activityRule = ActivityScenarioRule(ShowdownLogParityHarnessActivity::class.java)

    @Test
    fun ShowdownMoveEffectsView_CreatedByTheHostActivity_IsAttachedToItsWindow() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity { activity = it }

        val parent = activity.renderer.parent

        assertNotNull("The battle scene view is not attached to a window", parent)
    }

    @Test
    fun ShowdownMoveEffectsView_GivenAMinimalBattle_LoadsItsPageAndReportsTheBattleLog() {
        lateinit var activity: ShowdownLogParityHarnessActivity
        activityRule.scenario.onActivity { activity = it }
        val session = BattleSession().apply { setLocalUsername("PLAYER") }
        session.applyProtocolPacket(minimalBattle)
        val generation = session.battleLogGeneration()
        val synchronizedBefore = activity.synchronizedGenerations.size

        activity.renderer.applyProtocol(minimalBattle, generation)
        val synchronized = activity.awaitBattleLogSynchronization(synchronizedBefore, generation, pageLoadTimeoutMillis)

        assertTrue("The battle scene page did not load and sync within $pageLoadTimeoutMillis ms", synchronized)
        assertTrue("The page raised JavaScript errors: ${activity.javascriptErrors}", activity.javascriptErrors.isEmpty())
    }
}
