package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownGlobalProtocolTest {
    @Test
    fun parsesGlobalNotifications() {
        assertEquals(
            ShowdownGlobalProtocol.Notification("Announcement", "Welcome back"),
            ShowdownGlobalProtocol.notification("|notify|Announcement|Welcome back|welcome")
        )
        assertEquals(
            ShowdownGlobalProtocol.Notification("", "The server will restart soon."),
            ShowdownGlobalProtocol.notification("|notify||The server will restart soon.")
        )
    }

    @Test
    fun rejectsUnrelatedOrEmptyGlobalNotifications() {
        assertNull(ShowdownGlobalProtocol.notification("|message|Announcement|Welcome back"))
        assertNull(ShowdownGlobalProtocol.notification("|notify|Announcement|"))
        assertNull(ShowdownGlobalProtocol.notification("Welcome back"))
    }

    @Test
    fun presentsGlobalNotificationsInActivityWithoutAddingBattleActions() {
        val session = BattleSession()
        session.prepareForLobby()

        session.presentSystemNotice("Announcement", "Welcome back")

        assertTrue(session.activityMessages().contains("[Announcement] Welcome back"))
        assertFalse(session.battleLog().contains("[Announcement] Welcome back"))
        assertEquals("[Announcement] Welcome back", session.status)
    }
}
