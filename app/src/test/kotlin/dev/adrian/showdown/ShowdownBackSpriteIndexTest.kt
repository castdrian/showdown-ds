package dev.adrian.showdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ShowdownBackSpriteIndexTest {
    @Test
    fun selectsExistingNormalBackAssetsAndSkipsFrontAndShinyEntries() {
        val html = """
            <img src="/imagenes/espada_escudo/sprites/animados/corviknight-back.gif">
            <a href="/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back.gif">
            <a href="/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back-s.gif">
            <img src="/imagenes/espada_escudo/sprites/animados/corviknight.gif">
            <img src="/imagenes/xy/sprites/animados-espalda/tornadus.gif">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back.gif",
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados/corviknight-back.gif"
            ),
            ShowdownBackSpriteIndex.candidates(html, listOf("Corviknight"))
        )
    }

    @Test
    fun matchesBackOnlyRootsWithoutBackFilenameSuffix() {
        val html = """
            <img src="/imagenes/xy/sprites/animados-espalda/tornadus.gif">
            <img src="/imagenes/xy/sprites/animados-espalda/tornadus-s.gif">
        """.trimIndent()

        assertEquals(
            listOf("https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda/tornadus.gif"),
            ShowdownBackSpriteIndex.candidates(html, listOf("Tornadus"))
        )
    }

    @Test
    fun selectsTheMatchingShinyBackAssetWhenRequested() {
        val html = """
            <img src="/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back.gif">
            <img src="/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back-s.gif">
            <img src="/imagenes/espada_escudo/sprites/animados/corviknight-back-s.gif">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/corviknight-back-s.gif",
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados/corviknight-back-s.gif"
            ),
            ShowdownBackSpriteIndex.candidates(html, listOf("Corviknight"), shiny = true)
        )
    }

    @Test
    fun recognizesGenSixShinyBackIndexRootsWithoutAFilenameSuffix() {
        val html = """
            <img src="/imagenes/rubi-omega-zafiro-alfa/sprites/animados-espalda-shiny/altaria-mega.gif">
            <img src="/imagenes/xy/sprites/animados-espalda-shiny/altaria-mega.gif">
            <img src="/imagenes/xy/sprites/animados-espalda/altaria-mega.gif">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/rubi-omega-zafiro-alfa/sprites/animados-espalda-shiny/altaria-mega.gif",
                "https://www.pkparaiso.com/imagenes/xy/sprites/animados-espalda-shiny/altaria-mega.gif"
            ),
            ShowdownBackSpriteIndex.candidates(html, listOf("Altaria-Mega"), shiny = true)
        )
    }

    @Test
    fun keepsGiantBackAssetsAheadOfOtherAnimatedHdSources() {
        val html = """
            <a href="/imagenes/espada_escudo/sprites/animados-gigante/alcremie-back.gif">
            <img src="/imagenes/espada_escudo/sprites/animados/alcremie-back.gif">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/espada_escudo/sprites/animados-gigante/alcremie-back.gif"
            ),
            ShowdownBackSpriteIndex.highResolutionCandidates(html, listOf("Alcremie"))
        )
    }

    @Test
    fun recognizesTheUltraSunUltraMoonGiantBackRootWhenPublished() {
        val html = """
            <a href="/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/decidueye-back.gif">
            <img src="/imagenes/ultra_sol_ultra_luna/sprites/animados-espalda/decidueye.gif">
        """.trimIndent()

        assertEquals(
            listOf(
                "https://www.pkparaiso.com/imagenes/ultra_sol_ultra_luna/sprites/animados-sinbordes-gigante/decidueye-back.gif"
            ),
            ShowdownBackSpriteIndex.highResolutionCandidates(html, listOf("Decidueye"))
        )
    }
}
