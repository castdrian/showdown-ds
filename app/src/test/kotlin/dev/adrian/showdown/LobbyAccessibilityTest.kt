package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class LobbyAccessibilityTest {
    @Test
    fun LobbyAccessibility_DescribeIdleLobby_IncludesTheCurrentStatus() {
        val description = LobbyAccessibility.describe(replayLoading = false, status = "Signed in as LocalUser.")

        assertEquals("Showdown lobby. Signed in as LocalUser.", description)
    }

    @Test
    fun LobbyAccessibility_DescribeLoadingReplay_ReportsTheReplayLoading() {
        val description = LobbyAccessibility.describe(replayLoading = true, status = "Ignored while loading.")

        assertEquals("Showdown replay. Loading battle.", description)
    }
}
