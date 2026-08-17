package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownCachePathsTest {
    @Test
    fun givesExtensionlessApiResponsesABinaryCacheExtension() {
        assertEquals("bin", showdownCacheExtension("https://pokeapi.co/api/v2/pokemon/toedscruel"))
    }

    @Test
    fun keepsKnownResourceExtensions() {
        assertEquals("gif", showdownCacheExtension("https://play.pokemonshowdown.com/sprites/xyani/rotomwash.gif"))
        assertEquals("png", showdownCacheExtension("https://raw.githubusercontent.com/PokeAPI/sprites/master/sprites/pokemon/other/home/1014.png"))
    }

    @Test
    fun ignoresQueryStringsAndInvalidExtensions() {
        assertEquals("json", showdownCacheExtension("https://example.test/data/moves.json?cache=1"))
        assertEquals("bin", showdownCacheExtension("https://pokeapi.co/api/v2/pokemon/toedscruel?format=raw"))
    }
}
