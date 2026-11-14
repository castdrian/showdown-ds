package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownUnfinishedBattlesTest {
    private val room = "battle-gen9randombattle-2"
    private val format = "[Gen 9] Random Battle"
    private val reportedGames = mapOf(room to format)
    private val initPacket = listOf(
        "|init|battle",
        "|title|CaptureAlpha vs. StallBot",
        "|player|p1|CaptureAlpha|169|",
        "|player|p2|StallBot|169|"
    )
    private val requestPacket = listOf("|request|{\"active\":[]}")

    private fun loggedInTracker(): ShowdownUnfinishedBattles = ShowdownUnfinishedBattles().apply { onNamedLogin() }

    @Test
    fun UnfinishedBattles_GamesReportedAfterNamedLogin_AreHeld() {
        val tracker = loggedInTracker()

        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        assertTrue(tracker.isHeld(room))
    }

    @Test
    fun UnfinishedBattles_GamesReportedWithoutNamedLogin_AreNotHeld() {
        val tracker = ShowdownUnfinishedBattles()

        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        assertFalse(tracker.isHeld(room))
    }

    @Test
    fun UnfinishedBattles_RoomTheCallerExpects_IsNotHeld() {
        val tracker = loggedInTracker()

        tracker.onSearchUpdate(reportedGames, expectedRoomIds = setOf(room))

        assertFalse(tracker.isHeld(room))
    }

    @Test
    fun UnfinishedBattles_BattleAppearingAfterTheLoginSnapshot_IsNotHeld() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(emptyMap(), expectedRoomIds = emptySet())

        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        assertFalse(tracker.isHeld(room))
    }

    @Test
    fun UnfinishedBattles_HeldRoomPackets_AreReleasedInOrder() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())
        tracker.holdPacket(room, initPacket)
        tracker.holdPacket(room, requestPacket)

        val released = tracker.release(room)

        assertEquals(listOf(initPacket, requestPacket), released)
    }

    @Test
    fun UnfinishedBattles_HeldRoomIds_ListsEveryHeldRoom() {
        val tracker = loggedInTracker()
        val secondRoom = "battle-gen9ou-7"

        tracker.onSearchUpdate(reportedGames + (secondRoom to "[Gen 9] OU"), expectedRoomIds = emptySet())

        assertEquals(setOf(room, secondRoom), tracker.heldRoomIds())
    }

    @Test
    fun UnfinishedBattles_ReleasedRoom_IsNoLongerHeld() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        tracker.release(room)

        assertFalse(tracker.isHeld(room))
    }

    @Test
    fun UnfinishedBattles_Entries_ShowFormatAndOpponentTitle() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())
        tracker.holdPacket(room, initPacket)

        val entries = tracker.entries()

        assertEquals(
            listOf(ShowdownUnfinishedBattles.Entry(room, format, "CaptureAlpha vs. StallBot")),
            entries
        )
    }

    @Test
    fun UnfinishedBattles_EntryWithoutTitleYet_FallsBackToTheRoomId() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        val entries = tracker.entries()

        assertEquals(room, entries.single().title)
    }

    @Test
    fun UnfinishedBattles_FormatWithTheNoRenamesMarker_IsShownWithoutIt() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(mapOf(room to "$format*"), expectedRoomIds = emptySet())

        val entries = tracker.entries()

        assertEquals(format, entries.single().format)
    }

    @Test
    fun UnfinishedBattles_HeldRoomsWithoutTitlesYet_AreNotReadyToPresent() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        assertFalse(tracker.isReadyToPresent())
    }

    @Test
    fun UnfinishedBattles_EveryHeldRoomTitled_IsReadyToPresent() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())
        tracker.holdPacket(room, initPacket)

        assertTrue(tracker.isReadyToPresent())
    }

    @Test
    fun UnfinishedBattles_NothingHeld_IsNotReadyToPresent() {
        val tracker = loggedInTracker()

        assertFalse(tracker.isReadyToPresent())
    }

    @Test
    fun UnfinishedBattles_BattleEndsWhileHeld_IsDropped() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        tracker.holdPacket(room, listOf("|win|StallBot"))

        assertFalse(tracker.isHeld(room))
        assertTrue(tracker.entries().isEmpty())
    }

    @Test
    fun UnfinishedBattles_RoomNoLongerReportedByServer_IsDropped() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        tracker.onSearchUpdate(emptyMap(), expectedRoomIds = emptySet())

        assertFalse(tracker.isHeld(room))
    }

    @Test
    fun UnfinishedBattles_ConnectionReset_ForgetsHeldBattlesAndTheLoginSnapshot() {
        val tracker = loggedInTracker()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        tracker.onConnectionReset()
        tracker.onSearchUpdate(reportedGames, expectedRoomIds = emptySet())

        assertFalse(tracker.isHeld(room))
    }

    @Test
    fun UnfinishedBattles_PacketsForRoomsThatAreNotHeld_AreIgnored() {
        val tracker = loggedInTracker()

        tracker.holdPacket("battle-other-1", initPacket)

        assertTrue(tracker.release("battle-other-1").isEmpty())
    }
}
