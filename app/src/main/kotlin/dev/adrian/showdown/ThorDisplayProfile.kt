package dev.adrian.showdown

enum class ThorDisplayKind {
    UPPER,
    LOWER
}

object ThorDisplayProfile {
    const val UPPER_WIDTH_PIXELS = 1920
    const val UPPER_HEIGHT_PIXELS = 1080
    const val LOWER_WIDTH_PIXELS = 1240
    const val LOWER_HEIGHT_PIXELS = 1080

    fun kindFor(widthPixels: Int, heightPixels: Int): ThorDisplayKind = if (
        widthPixels <= 1400 && heightPixels >= 900 && widthPixels.toFloat() / heightPixels < 1.4f
    ) {
        ThorDisplayKind.LOWER
    } else {
        ThorDisplayKind.UPPER
    }

    fun minimumReadablePixels(widthPixels: Int, heightPixels: Int): Float = when (kindFor(widthPixels, heightPixels)) {
        ThorDisplayKind.UPPER -> 24f
        ThorDisplayKind.LOWER -> 32f
    }
}
