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
    const val UPPER_DIAGONAL_MILLIMETRES = 152.4f
    const val LOWER_DIAGONAL_MILLIMETRES = 99.6f
    const val ENCLOSURE_WIDTH_MILLIMETRES = 150f
    const val ENCLOSURE_HEIGHT_MILLIMETRES = 94f
    const val ENCLOSURE_DEPTH_MILLIMETRES = 25.6f
    const val LOWER_MINIMUM_TEXT_SP = 18f

    fun kindFor(widthPixels: Int, heightPixels: Int): ThorDisplayKind = if (
        widthPixels <= 1400 && heightPixels >= 900 && widthPixels.toFloat() / heightPixels < 1.4f
    ) {
        ThorDisplayKind.LOWER
    } else {
        ThorDisplayKind.UPPER
    }

    fun minimumReadablePixels(
        widthPixels: Int,
        heightPixels: Int,
        scaledDensity: Float = 1f,
        minimumTextSp: Float = LOWER_MINIMUM_TEXT_SP
    ): Float = when (kindFor(widthPixels, heightPixels)) {
        ThorDisplayKind.UPPER -> 24f
        ThorDisplayKind.LOWER -> maxOf(32f, minimumTextSp * scaledDensity)
    }
}
