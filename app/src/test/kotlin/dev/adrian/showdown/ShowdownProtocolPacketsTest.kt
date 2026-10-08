package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownProtocolPacketsTest {
    @Test
    fun separatesGlobalAndRoomPacketsFromOneServerFrame() {
        val packets = ShowdownProtocolPackets.decode(
            "|challstr|1|token\n\n>lobby\n|updateuser|Guest\n>battle-gen9ou-1\n|init|battle\n|turn|1\n"
        )

        assertEquals(
            listOf(
                ShowdownRoomPacket(null, listOf("|challstr|1|token")),
                ShowdownRoomPacket("lobby", listOf("|updateuser|Guest")),
                ShowdownRoomPacket("battle-gen9ou-1", listOf("|init|battle", "|turn|1"))
            ),
            packets
        )
    }

    @Test
    fun ignoresEmptyPayloadsAndEmptyLines() {
        val packets = ShowdownProtocolPackets.decode("\n>lobby\n>battle-gen9ou-1\n\n|init|battle\n\n")

        assertEquals(
            listOf(ShowdownRoomPacket("battle-gen9ou-1", listOf("|init|battle"))),
            packets
        )
    }

    @Test
    fun removesCarriageReturnsFromCrLfProtocolFrames() {
        val packets = ShowdownProtocolPackets.decode(
            "|challstr|1|token\r\n\r\n>lobby\r\n|updateuser|Guest\r\n"
        )

        assertEquals(
            listOf(
                ShowdownRoomPacket(null, listOf("|challstr|1|token")),
                ShowdownRoomPacket("lobby", listOf("|updateuser|Guest"))
            ),
            packets
        )
    }
}
