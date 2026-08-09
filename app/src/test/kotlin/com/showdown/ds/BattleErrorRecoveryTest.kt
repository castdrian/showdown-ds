package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BattleErrorRecoveryTest {
    @Test
    fun invalidMoveChoiceRestoresTheDecisionPanel() {
        val session = BattleSession()
        session.applyProtocolLine("|request|{\"rqid\":21,\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}]}")
        session.confirmSelection()

        assertEquals(false, session.decisionAvailable)
        session.applyProtocolLine("|error|[Invalid choice]")

        assertTrue(session.decisionAvailable)
        assertEquals(BattleSession.Panel.MOVES, session.panel)
        assertEquals("Choose a move", session.status)
    }

    @Test
    fun sentChoiceKeepsAReplayedRequestLocked() {
        val session = BattleSession()
        session.applyProtocolLine("|request|{\"rqid\":22,\"active\":[{\"moves\":[{\"move\":\"Tackle\",\"pp\":35}]}]}")

        session.applyProtocolLine("|sentchoice|move 1|22")

        assertEquals(false, session.decisionAvailable)
        assertEquals("Choice sent. Waiting for the other player…", session.status)
        assertEquals(BattleSession.Panel.MOVES, session.panel)
    }

    @Test
    fun tieAndPrematureEndMarkTheBattleFinished() {
        val session = BattleSession()

        session.applyProtocolLine("|tie|The battle was a draw.")

        assertTrue(session.isBattleFinished())
        assertEquals("The battle was a draw.", session.status)
        assertEquals(false, session.decisionAvailable)
    }
}
