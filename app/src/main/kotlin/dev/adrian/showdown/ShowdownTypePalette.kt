package dev.adrian.showdown

import java.util.Locale

object ShowdownTypePalette {
    fun canonical(type: String): Int = when (type.trim().uppercase(Locale.ROOT)) {
        "NORMAL" -> rgb(145, 151, 159)
        "FIRE" -> rgb(250, 112, 60)
        "WATER" -> rgb(93, 144, 246)
        "ELECTRIC" -> rgb(245, 202, 48)
        "GRASS" -> rgb(93, 194, 102)
        "ICE" -> rgb(104, 204, 221)
        "FIGHTING" -> rgb(240, 129, 4)
        "POISON" -> rgb(177, 83, 188)
        "GROUND" -> rgb(208, 150, 77)
        "FLYING" -> rgb(125, 144, 236)
        "PSYCHIC" -> rgb(241, 91, 151)
        "BUG" -> rgb(151, 181, 50)
        "ROCK" -> rgb(178, 145, 67)
        "GHOST" -> rgb(105, 88, 173)
        "DRAGON" -> rgb(105, 83, 236)
        "DARK" -> rgb(118, 88, 72)
        "STEEL" -> rgb(126, 145, 171)
        "FAIRY" -> rgb(232, 128, 177)
        else -> rgb(89, 107, 127)
    }

    private fun rgb(red: Int, green: Int, blue: Int): Int =
        (0xff shl 24) or (red shl 16) or (green shl 8) or blue
}
