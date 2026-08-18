package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownFrontSpriteIndexTest {
    @Test
    fun selectsHdFrontAssetsAndSkipsBackAndShinyEntries() {
        val html = """
            <img src="/imagenes/espada_escudo/sprites/animados-gigante/mandibuzz.gif">
            <img src="/imagenes/espada_escudo/sprites/animados-gigante/mandibuzz-s.gif">
            <img src="/imagenes/xy/sprites/animados-espalda/mandibuzz.gif">
            <a href="https://www.pkparaiso.com/imagenes/xy/sprites/animados/mandibuzz.gif">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/mandibuzz.gif",
                "https://www.pkparaiso.com/imagenes/xy/sprites/animados/mandibuzz.gif"
            ),
            ShowdownFrontSpriteIndex.candidates(html, listOf("Mandibuzz"))
        )
    }

    @Test
    fun selectsShinyFormsAndNormalizesHttpLinks() {
        val html = """
            <img src="http://www.pkparaiso.com/imagenes/xy/sprites/animados-shiny/altaria.gif">
            <img src="/imagenes/xy/sprites/animados/altaria.gif">
            <img src="/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/altaria-s.gif">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/altaria-s.gif",
                "https://www.pkparaiso.com/imagenes/xy/sprites/animados-shiny/altaria.gif"
            ),
            ShowdownFrontSpriteIndex.candidates(html, listOf("Altaria"), shiny = true)
        )
    }

    @Test
    fun separatesGiantAssetsFromRegularAnimatedFallbacks() {
        val html = """
            <img src="/imagenes/xy/sprites/animados/masquerain.gif">
            <img src="/imagenes/espada_escudo/sprites/animados-gigante/masquerain.gif">
        """.trimIndent()

        assertEquals(
            listOf("https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/masquerain.gif"),
            ShowdownFrontSpriteIndex.highResolutionCandidates(html, listOf("Masquerain"))
        )
    }
}
