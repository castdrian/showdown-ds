package com.showdown.ds

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownSearchFlowTest {
    @Test
    fun waitsForGuestIdentityBeforeSearchingTheRequestedFormat() {
        val flow = ShowdownSearchFlow()

        flow.begin("gen7randombattle")

        assertEquals(listOf("/trn Adrian,0,"), flow.onTransportConnected("Adrian"))
        assertTrue(flow.onProtocol(listOf("|challstr|1|challenge")).isEmpty())
        assertEquals(
            listOf("/search gen7randombattle"),
            flow.onProtocol(listOf("|updateuser|Adrian|1|0"))
        )
    }

    @Test
    fun cancelsQueuedSearchesWhenTheConnectionIsClosed() {
        val flow = ShowdownSearchFlow()

        flow.begin("gen9randombattle")
        flow.onTransportConnected("Adrian")
        flow.cancel()

        assertTrue(flow.onProtocol(listOf("|updateuser|Adrian|1|0")).isEmpty())
    }

    @Test
    fun normalizesTheGuestNameForTheWireProtocol() {
        val flow = ShowdownSearchFlow()

        flow.begin("gen7randombattle")

        assertEquals(listOf("/trn Adrian DS,0,"), flow.onTransportConnected("  Adrian / DS  "))
    }
}
