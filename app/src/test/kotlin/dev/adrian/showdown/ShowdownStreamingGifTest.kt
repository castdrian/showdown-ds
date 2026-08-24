package dev.adrian.showdown

import org.junit.Assert.assertArrayEquals
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
}
