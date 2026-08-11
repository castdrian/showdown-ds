package dev.adrian.showdown

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
    fun buildsPrivacyAndDeleteCommands() {
        assertEquals("/teams setprivacy 42,yes", ShowdownTeamRemote.privacyCommand("42", true))
        assertEquals("/teams setprivacy 42,no", ShowdownTeamRemote.privacyCommand("42", false))
        assertEquals("/teams delete 42", ShowdownTeamRemote.deleteCommand("42"))
    }

    @Test
    fun parsesPrivacyUpdatesAndDeletes() {
        assertEquals(
            ShowdownTeamPrivacyUpdate("42", "secret"),
            ShowdownTeamRemote.parsePrivacyUpdate("|queryresponse|teamupdate|{\"teamid\":42,\"privacy\":\"secret\"}")
        )
        assertEquals(
            ShowdownTeamPrivacyUpdate("42", null),
            ShowdownTeamRemote.parsePrivacyUpdate("|queryresponse|teamupdate|{\"teamid\":42,\"privacy\":null}")
        )
        assertEquals("42", ShowdownTeamRemote.parseDeleted("|popup|Team 42 deleted."))
        assertNull(ShowdownTeamRemote.parseDeleted("|popup|Team 42 not found."))
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

    @Test
    fun parsesRemoteTeamPagesAndBuildsNavigationCommands() {
        val state = ShowdownTeamRemoteState()
        val html = """
            <div class="ladder pad"><h2>adrian's last 1 team</h2><hr />
            <strong>Rain team</strong><br /><small>Uploaded by: <strong>adrian</strong></small><br />
            <small>Uploaded on: today</small><br /><small>Format: [Gen 9] OU</small>
            <br /><a class="subtle" href="/view-team-42-secret">Pikachu</a><br />
            <a href="https://psim.us/t/42-secret">View full team</a><hr />
            Pikachu @ Light Ball<br />Ability: Static<br />- Thunderbolt<hr />
            Rotom-Wash @ Leftovers<br />Ability: Levitate<br />- Hydro Pump</div>
        """.trimIndent()

        assertTrue(state.applyProtocol("view-teams-all", listOf("|pagehtml|$html")))
        val team = state.snapshot.teams.single()
        assertEquals("42", team.remoteId)
        assertEquals("secret", team.privateKey)
        assertEquals("Rain team", team.name)
        assertEquals("[Gen 9] OU", team.formatLabel)
        assertEquals("gen9ou", team.formatId)
        assertEquals("adrian", team.owner)
        assertEquals(2, state.snapshot.packed?.split(']')?.size)
        assertEquals(
            listOf("Pikachu", "Rotom-Wash"),
            ShowdownTeamCodec.unpack(state.snapshot.packed.orEmpty()).map { it.species }
        )
        assertEquals("/join view-teams-view-42-secret", ShowdownTeamRemoteState.viewCommand(team))
        assertEquals("/join view-teams-all", ShowdownTeamRemoteState.ownTeamsCommand())
        assertEquals("/join view-teams-browse", ShowdownTeamRemoteState.browseCommand())
        assertEquals(
            "/join view-teams-searchpublic---gen9ou--Pikachu--Thunderbolt--Static--9",
            ShowdownTeamRemoteState.searchCommand("gen9ou", "Pikachu", "Thunderbolt", "Static", "9")
        )
        assertTrue(state.applyProtocol("view-teams-all", listOf("|popup|Teams are unavailable.")))
        assertEquals("Teams are unavailable.", state.snapshot.error)
    }

    @Test
    fun decodesUrlEncodedRemoteTeamLabels() {
        val state = ShowdownTeamRemoteState()
        val html = "<strong>Demo%20team</strong><small>Uploaded by: <strong>adrian%20test</strong></small><small>Format: [Gen%209]%20OU</small><a class=\"subtle\" href=\"/view-team-42\">Team</a>"

        assertTrue(state.applyProtocol("view-teams-all", listOf("|pagehtml|$html")))

        val team = state.snapshot.teams.single()
        assertEquals("Demo team", team.name)
        assertEquals("[Gen 9] OU", team.formatLabel)
        assertEquals("gen9ou", team.formatId)
        assertEquals("adrian test", team.owner)
    }

    @Test
    fun derivesShowdownFormatIdsFromServerLabels() {
        assertEquals("gen9ou", ShowdownTeamRemoteState.formatIdFromLabel("[Gen 9] OU"))
        assertEquals("gen9randombattle", ShowdownTeamRemoteState.formatIdFromLabel("[Gen 9] Random Battle"))
        assertEquals("gen9nationaldex", ShowdownTeamRemoteState.formatIdFromLabel("[Gen 9] National Dex"))
        val formats = listOf(
            BattleSession.MatchFormat("gen9ou", "[Gen 9] OU"),
            BattleSession.MatchFormat("gen9vgc2024regf", "[Gen 9] VGC 2024 Regulation F")
        )
        assertEquals("gen9ou", ShowdownTeamRemoteState.resolveFormatId("[Gen 9] OU", formats))
        assertEquals("gen9vgc2024regf", ShowdownTeamRemoteState.resolveFormatId("[Gen 9] VGC 2024 Regulation F", formats))
        assertNull(ShowdownTeamRemoteState.resolveFormatId("[Gen 9] Unknown Format", formats))
    }
}
