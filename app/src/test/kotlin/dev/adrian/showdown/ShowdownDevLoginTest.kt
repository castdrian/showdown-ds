package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownDevLoginTest {
    @Test
    fun ShowdownDevLogin_LocalBuildWithUsername_ReturnsTheTrimmedName() {
        val name = ShowdownDevLogin.usernameFrom("  LocalUser ", localBuild = true)

        assertEquals("LocalUser", name)
    }

    @Test
    fun ShowdownDevLogin_NonLocalBuild_IgnoresTheUsername() {
        val name = ShowdownDevLogin.usernameFrom("LocalUser", localBuild = false)

        assertEquals(null, name)
    }

    @Test
    fun ShowdownDevLogin_BlankOrMissingUsername_ReturnsNothing() {
        val blank = ShowdownDevLogin.usernameFrom("   ", localBuild = true)
        val missing = ShowdownDevLogin.usernameFrom(null, localBuild = true)

        assertEquals(null, blank)
        assertEquals(null, missing)
    }

    @Test
    fun ShowdownDevLogin_RenameCommand_UsesTheEmptyTokenAGuestSecurityFreeServerAccepts() {
        val command = ShowdownDevLogin.renameCommand("LocalUser")

        assertEquals("/trn LocalUser,0,", command)
    }
}
