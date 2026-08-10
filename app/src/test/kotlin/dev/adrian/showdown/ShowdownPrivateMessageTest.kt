package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownPrivateMessageTest {
    @Test
    fun parsesPrivateMessagesAndFindsTheOtherPlayer() {
        val message = ShowdownPrivateMessages.parse("|pm|Gladion|Adrian|Meet in lobby|now")

        assertEquals(ShowdownPrivateMessage("Gladion", "Adrian", "Meet in lobby|now"), message)
        assertEquals("Gladion", ShowdownPrivateMessages.target(message!!, "Adrian"))
    }

    @Test
    fun buildsPrivateMessageCommands() {
        assertEquals("/pm Gladion, Hello there", ShowdownPrivateMessages.command(" Gladion ", " Hello there "))
        assertNull(ShowdownPrivateMessages.parse("|chat|Lobby|hello"))
    }

    @Test
    fun parsesShowdownChallengeNoticesWithoutTreatingThemAsChat() {
        val message = ShowdownPrivateMessages.parse(
            "|pm|Gladion|Adrian|/challenge gen7randombattle|||Accept|Reject"
        )!!

        assertEquals(ShowdownChallengeNotice("gen7randombattle"), ShowdownPrivateMessages.challenge(message))
        assertNull(ShowdownPrivateMessages.challenge(message.copy(text = "/challenge")))
        assertNull(ShowdownPrivateMessages.challenge(message.copy(text = "/challenger gen7randombattle")))
        assertNull(ShowdownPrivateMessages.challenge(message.copy(text = "Want to battle?")))
    }
}
