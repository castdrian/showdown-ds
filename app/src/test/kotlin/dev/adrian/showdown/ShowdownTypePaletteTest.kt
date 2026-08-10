package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ShowdownTypePaletteTest {
    @Test
    fun usesTheOfficialFightingTypeOrange() {
        assertEquals(0xfff08104.toInt(), ShowdownTypePalette.canonical("Fighting"))
        assertNotEquals(ShowdownTypePalette.canonical("Fire"), ShowdownTypePalette.canonical("Fighting"))
    }
}
