package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownMoveEffectsQueueTest {
    @Test
    fun authoritativeSeedReplacesPacketsCollectedBeforeWebViewReady() {
        val queue = ShowdownMoveEffectsQueue()
        queue.add(listOf("|move|old|Tackle|target"))

        queue.resetWith(listOf("|init|battle", "|start", "|switch|p1a: Pikachu|Pikachu, L50|100/100"))

        assertEquals(
            listOf(ShowdownMoveEffectsQueue.Packet.Seed(listOf("|init|battle", "|start", "|switch|p1a: Pikachu|Pikachu, L50|100/100"))),
            listOfNotNull(queue.poll())
        )
        assertEquals(emptyList<List<String>>(), listOfNotNull(queue.poll()))
    }

    @Test
    fun emptyAuthoritativeSeedStillDiscardsStalePackets() {
        val queue = ShowdownMoveEffectsQueue()
        queue.add(listOf("|move|old|Tackle|target"))

        queue.resetWith(emptyList())

        assertEquals(emptyList<List<String>>(), listOfNotNull(queue.poll()))
    }

    @Test
    fun finalChunkCarriesTheNativeTranscriptSynchronizationBoundary() {
        val queue = ShowdownMoveEffectsQueue()
        queue.add(listOf("|move|Pikachu|Tackle|Eevee"), 12L, false)
        queue.add(listOf("|-damage|Eevee|90/100"), 12L, true)

        assertEquals(
            ShowdownMoveEffectsQueue.Packet.Receive(
                listOf("|move|Pikachu|Tackle|Eevee"),
                12L,
                false
            ),
            queue.poll()
        )
        assertEquals(
            ShowdownMoveEffectsQueue.Packet.Receive(
                listOf("|-damage|Eevee|90/100"),
                12L,
                true
            ),
            queue.poll()
        )
    }
}
