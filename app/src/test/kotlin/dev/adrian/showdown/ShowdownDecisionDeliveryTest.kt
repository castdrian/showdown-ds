package dev.adrian.showdown

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownDecisionDeliveryTest {
    @Test
    fun waitsForSentChoiceAcknowledgement() {
        assertFalse(
            ShowdownDecisionDelivery.shouldClearPendingCommand(
                "/choose move 1|17",
                listOf("|turn|1")
            )
        )
        assertTrue(
            ShowdownDecisionDelivery.shouldClearPendingCommand(
                "/choose move 1|17",
                listOf("|sentchoice|move 1|17")
            )
        )
    }

    @Test
    fun ignoresAStaleSentChoiceAcknowledgement() {
        assertFalse(
            ShowdownDecisionDelivery.shouldClearPendingCommand(
                "/choose move 1|17",
                listOf("|sentchoice|switch 2|16")
            )
        )
    }

    @Test
    fun clearsWhenTheServerMovesToAnotherRequest() {
        assertFalse(
            ShowdownDecisionDelivery.shouldClearPendingCommand(
                "/choose move 1|17",
                listOf("|request|{\"rqid\":17,\"active\":[]}")
            )
        )
        assertTrue(
            ShowdownDecisionDelivery.shouldClearPendingCommand(
                "/choose move 1|17",
                listOf("|request|{\"rqid\":18,\"active\":[]}")
            )
        )
    }

    @Test
    fun clearsWhenAnOlderServerDoesNotIncludeRequestIds() {
        assertTrue(
            ShowdownDecisionDelivery.shouldClearPendingCommand(
                "/choose move 1",
                listOf("|request|{\"active\":[]}")
            )
        )
    }

    @Test
    fun clearsWhenTheServerRejectsTheChoice() {
        assertTrue(
            ShowdownDecisionDelivery.shouldClearPendingCommand(
                "/choose move 1|17",
                listOf("|error|Can't move")
            )
        )
    }
}
