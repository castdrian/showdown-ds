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
}
