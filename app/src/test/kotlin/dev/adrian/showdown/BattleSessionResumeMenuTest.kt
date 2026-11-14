package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleSessionResumeMenuTest {
    private val resumeMenuIndex = 3

    private fun sessionOnMenu(): BattleSession = BattleSession().apply { selectPanel(BattleSession.Panel.MENU) }

    private fun BattleSession.publishedActions(): MutableList<BattleSession.ClientAction> {
        val actions = mutableListOf<BattleSession.ClientAction>()
        addClientActionListener { actions += it }
        return actions
    }

    @Test
    fun BattleSession_MenuWithUnfinishedBattles_OffersResumeBattle() {
        val session = sessionOnMenu()

        session.setUnfinishedBattleCount(1)

        assertEquals("Resume battle", session.menuItems()[resumeMenuIndex])
    }

    @Test
    fun BattleSession_MenuWithoutUnfinishedBattles_OffersChallengePlayer() {
        val session = sessionOnMenu()

        assertEquals("Challenge player", session.menuItems()[resumeMenuIndex])
    }

    @Test
    fun BattleSession_ConfirmingResumeBattle_PublishesResumeAction() {
        val session = sessionOnMenu()
        val actions = session.publishedActions()
        session.setUnfinishedBattleCount(2)
        session.selectMenuItem(resumeMenuIndex)

        session.confirmSelection()

        assertTrue(BattleSession.ClientAction.RESUME_BATTLE in actions)
    }

    @Test
    fun BattleSession_ClearingUnfinishedBattles_RestoresChallengePlayer() {
        val session = sessionOnMenu()
        session.setUnfinishedBattleCount(1)

        session.setUnfinishedBattleCount(0)

        assertEquals("Challenge player", session.menuItems()[resumeMenuIndex])
    }
}
