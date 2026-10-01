package dev.adrian.showdown

import java.util.Locale

enum class BattleWeatherVisual {
    RAIN,
    SUN,
    SANDSTORM,
    SNOW,
    STRONG_WINDS
}

enum class BattleTerrainVisual {
    ELECTRIC,
    GRASSY,
    MISTY,
    PSYCHIC
}

enum class BattleFieldOverlay {
    GRAVITY,
    TRICK_ROOM,
    MAGIC_ROOM,
    WONDER_ROOM
}

data class BattleFieldVisuals(
    val weather: BattleWeatherVisual?,
    val terrain: BattleTerrainVisual?,
    val overlays: Set<BattleFieldOverlay>
) {
    val hasActiveVisuals: Boolean
        get() = weather != null || terrain != null || overlays.isNotEmpty()
}

object BattleFieldVisualComposer {
    fun compose(info: BattleSession.BattleInfo): BattleFieldVisuals = BattleFieldVisuals(
        weather = weatherVisual(info.weather),
        terrain = terrainVisual(info.terrain),
        overlays = info.fieldEffects.mapNotNull(::fieldOverlay).toSet()
    )

    private fun weatherVisual(value: String): BattleWeatherVisual? = when (normalize(value)) {
        "raindance", "heavyrain" -> BattleWeatherVisual.RAIN
        "sun", "sunnyday", "harshsunshine", "desolateland" -> BattleWeatherVisual.SUN
        "sandstorm" -> BattleWeatherVisual.SANDSTORM
        "hail", "snow" -> BattleWeatherVisual.SNOW
        "strongwinds" -> BattleWeatherVisual.STRONG_WINDS
        else -> null
    }

    private fun terrainVisual(value: String): BattleTerrainVisual? = when (normalize(value)) {
        "electricterrain" -> BattleTerrainVisual.ELECTRIC
        "grassyterrain" -> BattleTerrainVisual.GRASSY
        "mistyterrain" -> BattleTerrainVisual.MISTY
        "psychicterrain" -> BattleTerrainVisual.PSYCHIC
        else -> null
    }

    private fun fieldOverlay(value: String): BattleFieldOverlay? = when (normalize(value)) {
        "gravity" -> BattleFieldOverlay.GRAVITY
        "trickroom" -> BattleFieldOverlay.TRICK_ROOM
        "magicroom" -> BattleFieldOverlay.MAGIC_ROOM
        "wonderroom" -> BattleFieldOverlay.WONDER_ROOM
        else -> null
    }

    private fun normalize(value: String) = value.filter(Char::isLetterOrDigit).lowercase(Locale.ROOT)
}
