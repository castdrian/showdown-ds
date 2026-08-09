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

    @Test
    fun handlesFullRoomProtocolMessagesAndPreservesChatPipes() {
        val state = ShowdownChatRoomState()

        state.applyProtocol(
            "lobby",
            listOf(
                "|init|chat",
                "|users|2,+Alice,bob",
                "|join|+Carol",
                "|chat|+Alice|hello|with|pipes",
                "|name|%Caroline|carol",
                "|leave|bob",
                "Alice was promoted by Mod.",
                "|battle|battle-gen9ou-1|Alice|Caroline",
                "|notify|Announcement|Welcome back",
                "|uhtml|poll|<b>Vote now</b>",
                "|uhtmlchange|poll|<b>Vote now, updated</b>"
            )
        )

        assertEquals(listOf("+Alice", "%Caroline"), state.users)
        assertEquals("hello|with|pipes", state.messages[0].text)
        assertEquals("Alice was promoted by Mod.", state.messages[1].text)
        assertEquals("Alice and Caroline started a battle.", state.messages[2].text)
        assertEquals("Announcement: Welcome back", state.messages[3].text)
        assertEquals("Vote now, updated", state.messages[4].text)
        assertTrue(state.messages.drop(1).all { it.system })
    }

    @Test
    fun handlesPlainRoomMessagesNotificationsAndRankedAwayUsers() {
        val state = ShowdownChatRoomState()

        state.applyProtocol(
            "lobby",
            listOf(
                "|init|chat",
                "||Server maintenance starts soon.",
                "|notify|Alert|Welcome back|welcome",
                "|join|★Ace@!",
                "|c|☆Ace@!|Hello there"
            )
        )

        assertEquals(listOf("★Ace@!"), state.users)
        assertEquals("Server maintenance starts soon.", state.messages[0].text)
        assertEquals("Alert: Welcome back", state.messages[1].text)
        assertEquals("Ace", state.messages[2].speaker)
    }

    @Test
    fun removesNamedHtmlWhenItIsCleared() {
        val state = ShowdownChatRoomState()

        state.applyProtocol("lobby", listOf("|init|chat", "|uhtml|poll|<b>Vote now</b>"))
        state.applyProtocol("lobby", listOf("|uhtmlchange|poll|"))

        assertTrue(state.messages.isEmpty())
    }
}
