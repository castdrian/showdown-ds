package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownUserDetailsTest {
    @Test
    fun parsesOnlineProfileAndVisibleRooms() {
        val profile = ShowdownUserDetails.parse(
            "|queryresponse|userdetails|{" +
                "\"id\":\"Alice\",\"userid\":\"alice\",\"name\":\"Alice\",\"avatar\":12," +
                "\"status\":\"Ready\",\"group\":\"+\",\"customgroup\":\"Section Leader\"," +
                "\"autoconfirmed\":true,\"rooms\":{" +
                    "\"lobby\":{},\"battle-gen9ou-1\":{" +
                        "\"p1\":\" Alice\",\"p2\":\" Bob\"" +
                    "}" +
                "},\"friended\":true}"
        )

        requireNotNull(profile)
        assertEquals("alice", profile.userid)
        assertEquals("Alice", profile.name)
        assertEquals("12", profile.avatar)
        assertEquals("Ready", profile.status)
        assertEquals("+", profile.group)
        assertEquals("Section Leader", profile.customGroup)
        assertTrue(profile.autoconfirmed)
        assertTrue(profile.online)
        assertTrue(profile.friended)
        assertEquals(listOf("lobby", "battle-gen9ou-1"), profile.rooms.map(ShowdownUserDetails.Room::id))
        assertEquals("Alice", profile.rooms[1].playerOne)
        assertEquals("Bob", profile.rooms[1].playerTwo)
    }

    @Test
    fun parsesOfflineProfileWithoutRooms() {
        val profile = ShowdownUserDetails.parse(
            "|queryresponse|userdetails|{\"userid\":\"bob\",\"name\":\"Bob\",\"rooms\":false}"
        )

        requireNotNull(profile)
        assertFalse(profile.online)
        assertTrue(profile.rooms.isEmpty())
    }

    @Test
    fun createsProfileCommands() {
        assertEquals("/cmd userdetails Alice", ShowdownUserDetails.queryCommand(" Alice "))
        assertEquals("/friend add Alice", ShowdownUserDetails.addFriendCommand(" Alice "))
    }

    @Test
    fun parsesPublicProfileRatings() {
        val profile = ShowdownUserDetails.parsePublicPayload(
            "{" +
                "\"username\":\"Alice\",\"userid\":\"alice\",\"ratings\":{" +
                "\"gen9ou\":{\"elo\":1823.4,\"gxe\":71.2}," +
                "\"gen9uu\":{\"elo\":1600,\"gxe\":64.5}" +
                "}}"
        )

        requireNotNull(profile)
        assertEquals("Alice", profile.name)
        assertEquals("gen9ou", profile.ratings.first().format)
        assertEquals(1823.4, profile.ratings.first().elo, 0.01)
    }
}
