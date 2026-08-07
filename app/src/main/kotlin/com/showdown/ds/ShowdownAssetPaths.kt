package com.showdown.ds

import java.util.Locale

object ShowdownAssetPaths {
    fun battleSprite(species: String, back: Boolean, style: BattleSession.SpriteStyle): String {
        val collection = if (style == BattleSession.SpriteStyle.MODERN_3D) "xyani" else "gen5ani"
        return "sprites/${if (back) "$collection-back" else collection}/${animationId(species)}.gif"
    }

    fun dexSprite(species: String) = "sprites/dex/${dexId(species)}.png"

    fun placeholder(back: Boolean) = "sprites/${if (back) "ani-back" else "ani"}/substitute.gif"

    fun trainer(trainer: String) = "sprites/trainers/${animationId(trainer)}.png"

    fun animationId(value: String) = value.lowercase(Locale.ROOT).replace(Regex("[^a-z0-9]"), "")

    private fun dexId(value: String): String {
        val original = value.trim().lowercase(Locale.ROOT)
        val normalized = original
            .replace("é", "e")
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return when {
            original.contains('♀') || normalized == "nidoran-f" -> "nidoran-f"
            original.contains('♂') || normalized == "nidoran-m" -> "nidoran-m"
            else -> normalized
        }
    }
}
