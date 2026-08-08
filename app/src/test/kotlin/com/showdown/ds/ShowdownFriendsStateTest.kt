package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownFriendsStateTest {
    @Test
    fun convertsFriendsPageHtmlToReadableText() {
        val state = ShowdownFriendsState()

        assertTrue(
            state.applyProtocol(
                "view-friends-all",
                listOf(
                    "|title|[Friends] All Friends",
                    "|pagehtml|<h3>Your friends:</h3><div><strong>Online</strong><br />Alice &amp; Bob</div>"
                )
            )
        )

        assertEquals("[Friends] All Friends", state.snapshot.title)
        assertEquals("Your friends:\nOnline\nAlice & Bob", state.snapshot.text)
    }

    @Test
    fun tracksFriendPageErrors() {
        val state = ShowdownFriendsState()

        state.applyProtocol("view-friends-all", listOf("|error|You must be autoconfirmed."))

        assertEquals("You must be autoconfirmed.", state.snapshot.error)
    }

    @Test
    fun createsFriendCommands() {
        assertEquals("/join view-friends-all", ShowdownFriendsState.pageCommand())
        assertEquals("/join view-friends-received", ShowdownFriendsState.pageCommand(" received "))
        assertEquals("/join view-friends-viewuser-alice", ShowdownFriendsState.publicListCommand(" Alice "))
        assertEquals("/friend add Alice", ShowdownFriendsState.addCommand(" Alice "))
        assertEquals("/friend remove Alice", ShowdownFriendsState.removeCommand(" Alice "))
        assertEquals("/friends accept Alice", ShowdownFriendsState.acceptCommand(" Alice "))
        assertEquals("/friends reject Alice", ShowdownFriendsState.rejectCommand(" Alice "))
    }
}
