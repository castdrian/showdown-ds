package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownChatRoomStateTest {
    @Test
    fun tracksRoomMetadataUsersAndMessages() {
        val state = ShowdownChatRoomState()

        state.applyProtocol(
            "lobby",
            listOf(
                "|init|chat",
                "|title|Lobby",
                "|users|3,+Alice, Bob, %Mod",
                "|c|+Alice|Hello everyone!",
                "|c:|12:30|%Mod|Keep it friendly"
            )
        )

        assertEquals("lobby", state.roomId)
        assertEquals("Lobby", state.title)
        assertEquals(listOf("+Alice", "Bob", "%Mod"), state.users)
        assertEquals("Alice", state.messages[0].speaker)
        assertEquals("Hello everyone!", state.messages[0].text)
        assertEquals("Mod", state.messages[1].speaker)
        assertEquals("Keep it friendly", state.messages[1].text)
    }

    @Test
    fun resetsRoomStateWhenJoiningAnotherRoom() {
        val state = ShowdownChatRoomState()
        state.applyProtocol("lobby", listOf("|init|chat", "|c|Alice|Hello"))

        state.applyProtocol("overused", listOf("|init|chat", "|title|OverUsed"))

        assertEquals("overused", state.roomId)
        assertTrue(state.messages.isEmpty())
        assertEquals("OverUsed", state.title)
    }

    @Test
    fun stripsMarkupFromSystemMessages() {
        val state = ShowdownChatRoomState()

        state.applyProtocol("lobby", listOf("|init|chat", "|raw|<b>Welcome</b>&nbsp;back"))

        assertEquals("Welcome back", state.messages.single().text)
        assertTrue(state.messages.single().system)
    }
}
