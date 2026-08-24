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

    @Test
    fun acceptsUltraSunUltraMoonBorderlessIndexLinks() {
        val html = """
            <a href="sprites_pokemon_sin_bordes.php?cid=3#group3">group</a>
        """.trimIndent()

        assertEquals(
            listOf("https://www.pkparaiso.com/ultra-sol-ultra-luna/sprites_pokemon_sin_bordes.php?cid=3&order=#sprites"),
            ShowdownSpriteIndexGroups.pageUrls(
                html,
                "https://www.pkparaiso.com/ultra-sol-ultra-luna/sprites_pokemon_sin_bordes.php"
            )
        )
    }

    @Test
    fun selectsOnlyTheIndexGroupContainingTheRequestedSpeciesRange() {
        val html = """
            <a class="grouplink" href="espada_escudo/sprites_pokemon.php?cid=0&order=#sprites">abomasnow.gif to alcremie.gif</a>
            <a class="grouplink" href="espada_escudo/sprites_pokemon.php?cid=1&order=#sprites">alcremie-s.gif to corviknight.gif</a>
            <a class="grouplink" href="espada_escudo/sprites_pokemon.php?cid=2&order=#sprites">dragapult.gif to zweilous.gif</a>
        """.trimIndent()

        assertEquals(
            listOf("https://www.pkparaiso.com/espada_escudo/sprites_pokemon.php?cid=1&order=#sprites"),
            ShowdownSpriteIndexGroups.pageUrls(
                html,
                "https://www.pkparaiso.com/espada_escudo/sprites_pokemon.php",
                listOf("Corviknight")
            )
        )
    }
}
