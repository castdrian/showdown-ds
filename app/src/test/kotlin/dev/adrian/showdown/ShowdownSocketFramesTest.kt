package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownSocketFramesTest {
    @Test
    fun wrapsCommandsInSockJsMessageArrays() {
        assertEquals("[\"|/search gen7randombattle\"]", ShowdownSocketFrames.encode("|/search gen7randombattle", true))
    }

    @Test
    fun leavesNativeWebSocketCommandsUnwrapped() {
        assertEquals("|/search gen7randombattle", ShowdownSocketFrames.encode("|/search gen7randombattle", false))
    }

    @Test
    fun decodesOpenAndRoomProtocolFrames() {
        assertEquals(ShowdownSocketFrame.Open, ShowdownSocketFrames.decode("o"))

        val frame = ShowdownSocketFrames.decode("a[\">battle-gen9ou-1\\n|init|battle\\n|turn|1\"]")

        assertTrue(frame is ShowdownSocketFrame.Messages)
        assertEquals(
            listOf(">battle-gen9ou-1\n|init|battle\n|turn|1"),
            (frame as ShowdownSocketFrame.Messages).values
        )
    }

    @Test
    fun decodesSocketClosuresAndPreservesRawFallbacks() {
        assertEquals(ShowdownSocketFrame.Closed(3000, "goodbye"), ShowdownSocketFrames.decode("c[3000,\"goodbye\"]"))
        assertEquals(ShowdownSocketFrame.Raw("not-a-sockjs-frame"), ShowdownSocketFrames.decode("not-a-sockjs-frame"))
    }
}
