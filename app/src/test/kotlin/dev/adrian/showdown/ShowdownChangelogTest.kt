package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShowdownChangelogTest {
    @Test
    fun presentsTheCurrentBuildChangesFirst() {
        val entries = ShowdownChangelog.entries("v0.1.1-alpha.60")

        assertEquals("v0.1.1-alpha.60", entries.first().version)
        assertTrue(entries.first().changes.any { it.contains("final Showdown result") })
        assertTrue(entries.first().changes.any { it.contains("0.5×–2×") })
        assertTrue(entries.first().changes.any { it.contains("lower display") })
        assertTrue(entries.first().changes.any { it.contains("HD-first") })
        assertTrue(entries.first().changes.any { it.contains("native battle animation timeline") })
        assertTrue(entries.first().changes.any { it.contains("Cancel search") })
        assertTrue(entries.first().changes.any { it.contains("memory pressure") })
        assertTrue(entries.first().changes.any { it.contains("custom dialogs") })
        assertTrue(entries.first().changes.any { it.contains("Team previews") && it.contains("shiny") })
    }
}
