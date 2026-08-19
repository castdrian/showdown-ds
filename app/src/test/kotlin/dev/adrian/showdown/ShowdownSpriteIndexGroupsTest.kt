package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownSpriteIndexGroupsTest {
    @Test
    fun extractsAndNormalizesPaginatedFrontPages() {
        val html = """
            <a class="grouplink" href="espada_escudo/sprites_pokemon.php?cid=2&order=#sprites">second</a>
            <a class="grouplink" href="espada_escudo/sprites_pokemon.php?cid=0#chunk0">first</a>
            <a class="grouplink" href="espada_escudo/sprites_pokemon.php?cid=2&order=#sprites">duplicate</a>
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/espada_escudo/sprites_pokemon.php?cid=0&order=#sprites",
                "https://www.pkparaiso.com/espada_escudo/sprites_pokemon.php?cid=2&order=#sprites"
            ),
            ShowdownSpriteIndexGroups.pageUrls(
                html,
                "https://www.pkparaiso.com/espada_escudo/sprites_pokemon.php"
            )
        )
    }

    @Test
    fun acceptsShinyBackIndexLinks() {
        val html = """
            <a href="rubi-omega-zafiro-alfa/sprites_pokemon_variocolores_espalda.php?cid=4&order=#sprites">group</a>
        """.trimIndent()

        assertEquals(
            listOf("https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon_variocolores_espalda.php?cid=4&order=#sprites"),
            ShowdownSpriteIndexGroups.pageUrls(
                html,
                "https://www.pkparaiso.com/rubi-omega-zafiro-alfa/sprites_pokemon_variocolores_espalda.php"
            )
        )
    }
}
