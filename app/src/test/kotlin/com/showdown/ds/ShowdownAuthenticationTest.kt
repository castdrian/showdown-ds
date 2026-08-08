package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShowdownAuthenticationTest {
    @Test
    fun extractsTheFullChallengeAndBuildsTheRenameCommand() {
        assertEquals("1|abc|def", ShowdownAuthentication.challenge("|challstr|1|abc|def"))
        assertEquals("/trn Adrian,0,token", ShowdownAuthentication.renameCommand("Adrian", "token"))
        assertEquals("/nick Misty", ShowdownAuthentication.guestRenameCommand(" Misty "))
    }

    @Test
    fun parsesTheLoginAssertionAndRejectsMalformedResponses() {
        assertEquals("token", ShowdownAuthentication.assertion("]{\"assertion\":\"token\"}"))
        assertNull(ShowdownAuthentication.assertion("invalid"))
    }

    @Test
    fun parsesNamedAndGuestUserUpdatesWithoutTheRankPrefix() {
        assertEquals(ShowdownAuthentication.UserUpdate("Adrian", true), ShowdownAuthentication.userUpdate("|updateuser| Adrian|1|1|{}"))
        assertEquals(ShowdownAuthentication.UserUpdate("Guest", false), ShowdownAuthentication.userUpdate("|updateuser| Guest|0|1|{}"))
    }

    @Test
    fun extractsServerErrorsForConnectionStatus() {
        assertEquals("That name is already taken.", ShowdownAuthentication.serverError("|nametaken|Adrian|That name is already taken."))
        assertEquals("Line one\nLine two", ShowdownAuthentication.serverError("|popup|Line one||Line two"))
        assertNull(ShowdownAuthentication.serverError("|updateuser| Guest|0"))
    }
}
