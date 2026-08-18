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
}
