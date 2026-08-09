package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownTeamRemoteTest {
    @Test
    fun parsesPrivateUploadResponse() {
        val result = ShowdownTeamRemote.parseUpload("|queryresponse|teamupload|{\"teamid\":42,\"privacy\":\"secret\"}")

        assertEquals(ShowdownTeamUploadResult("42", "secret"), result)
    }

    @Test
    fun parsesPublicUploadResponse() {
        val result = ShowdownTeamRemote.parseUpload("|queryresponse|teamupload|{\"teamid\":7,\"privacy\":null}")

        assertEquals(ShowdownTeamUploadResult("7", null), result)
    }

    @Test
    fun buildsSaveAndUpdateCommands() {
        assertEquals(
            "/teams save Team One,gen9ou,1,Pikachu|||thunderbolt",
            ShowdownTeamRemote.command("save", null, "Team, One", "gen9ou", true, "Pikachu|||thunderbolt")
        )
        assertEquals(
            "/teams update 42,Team One,gen9ou,0,Pikachu|||thunderbolt",
            ShowdownTeamRemote.command("update", "42", "Team One", "gen9ou", false, "Pikachu|||thunderbolt")
        )
    }

    @Test
    fun buildsShareUrls() {
        assertEquals("https://psim.us/t/42-secret", ShowdownTeamRemote.shareUrl("42", "secret"))
        assertEquals("https://psim.us/t/7", ShowdownTeamRemote.shareUrl("7", null))
        assertNull(ShowdownTeamRemote.parseUpload("|queryresponse|teamupload|not-json"))
    }

    @Test
    fun detectsLocalEditsAfterUpload() {
        val uploaded = ShowdownTeam(
            "local",
            "Rain team",
            "gen9ou",
            "Pikachu|||thunderbolt",
            "42",
            "secret",
            "Pikachu|||thunderbolt",
            "Rain team",
            "gen9ou"
        )
        assertFalse(uploaded.remoteNeedsUpload)
        assertTrue(uploaded.copy(packed = "Pikachu|||volttackle").remoteNeedsUpload)
        assertTrue(uploaded.copy(name = "Sun team").remoteNeedsUpload)
    }
}
