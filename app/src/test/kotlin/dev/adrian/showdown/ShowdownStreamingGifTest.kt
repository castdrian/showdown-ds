package dev.adrian.showdown

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownStreamingGifTest {
    @Test
    fun interlacedRowsFollowGifFourPassOrder() {
        assertArrayEquals(intArrayOf(), ShowdownStreamingGif.interlacedRows(0))
        assertArrayEquals(intArrayOf(0), ShowdownStreamingGif.interlacedRows(1))
        assertArrayEquals(intArrayOf(0, 1), ShowdownStreamingGif.interlacedRows(2))
        assertArrayEquals(intArrayOf(0, 4, 2, 6, 1, 3, 5), ShowdownStreamingGif.interlacedRows(7))
        assertArrayEquals(
            intArrayOf(0, 8, 4, 12, 2, 6, 10, 14, 1, 3, 5, 7, 9, 11, 13, 15),
            ShowdownStreamingGif.interlacedRows(16)
        )
    }

    @Test
    fun samplesAnimatedSourcesAboveThePlaybackFrameBudget() {
        assertArrayEquals(
            intArrayOf(0, 2),
            ShowdownStreamingGif.sampledFrameIndexes(3, 2)
        )
        val indexes = ShowdownStreamingGif.sampledFrameIndexes(118, 16)
        assertArrayEquals(intArrayOf(0, 117), intArrayOf(indexes.first(), indexes.last()))
    }

    @Test
    fun readsVariableWidthGifCodesAcrossByteBoundaries() {
        val expectedCodes = listOf(5 to 3, 0x12f to 9, 0xa53 to 12, 0x13 to 5)
        val reader = GifBitReader(packCodes(expectedCodes))

        expectedCodes.forEach { (expected, width) ->
            assertEquals(expected, reader.read(width))
        }
        assertEquals(0, reader.read(3))
        assertEquals(-1, reader.read(1))
    }

    @Test
    fun decodesGifLzwCodesWhenTheCodeWidthGrows() {
        val output = mutableListOf<Int>()
        val decoder = GifLzwDecoder()
        val codes = listOf(4 to 3, 0 to 3, 1 to 3, 0 to 3, 1 to 4, 0 to 4, 5 to 4)

        decoder.decode(2, packCodes(codes), GifPixelConsumer { output.add(it) })

        assertEquals(listOf(0, 1, 0, 1, 0), output)
    }

    @Test
    fun decodesGifLzwKwKwKSequence() {
        val output = mutableListOf<Int>()
        val decoder = GifLzwDecoder()
        val imageData = packCodes(listOf(4 to 3, 0 to 3, 6 to 3, 5 to 3))

        decoder.decode(2, imageData, GifPixelConsumer { output.add(it) })

        assertEquals(listOf(0, 0, 0), output)
        output.clear()

        decoder.decode(2, imageData, GifPixelConsumer { output.add(it) })

        assertEquals(listOf(0, 0, 0), output)
    }

    private fun packCodes(codes: List<Pair<Int, Int>>): ByteArray {
        val bits = codes.flatMap { (value, width) ->
            List(width) { bit -> (value ushr bit) and 1 }
        }
        return ByteArray((bits.size + 7) / 8) { byteIndex ->
            var value = 0
            repeat(8) { bit ->
                val index = byteIndex * 8 + bit
                if (index < bits.size) value = value or (bits[index] shl bit)
            }
            value.toByte()
        }
    }
}
