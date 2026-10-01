package dev.adrian.showdown

enum class BattleMoveVisualStyle {
    STATUS,
    CONTACT_STRIKE,
    GROUND_RIPPLE,
    FIRE_BURST,
    WATER_WAVE,
    ELECTRIC_ARC,
    ICE_SHARDS,
    LEAF_SPIRAL,
    PSYCHIC_PULSE,
    WIND_CRESCENT,
    TYPE_BURST
}

object BattleMoveVisualStyleResolver {
    fun resolve(moveInfo: BattleSession.MoveInfo?, type: String): BattleMoveVisualStyle {
        val category = moveInfo?.category.orEmpty().uppercase()
        if (category == "STATUS") return BattleMoveVisualStyle.STATUS
        if (moveInfo?.contact == true) return BattleMoveVisualStyle.CONTACT_STRIKE
        return when (type.uppercase()) {
            "GROUND" -> BattleMoveVisualStyle.GROUND_RIPPLE
            "FIRE" -> BattleMoveVisualStyle.FIRE_BURST
            "WATER" -> BattleMoveVisualStyle.WATER_WAVE
            "ELECTRIC" -> BattleMoveVisualStyle.ELECTRIC_ARC
            "ICE" -> BattleMoveVisualStyle.ICE_SHARDS
            "GRASS" -> BattleMoveVisualStyle.LEAF_SPIRAL
            "PSYCHIC" -> BattleMoveVisualStyle.PSYCHIC_PULSE
            "FLYING" -> BattleMoveVisualStyle.WIND_CRESCENT
            else -> BattleMoveVisualStyle.TYPE_BURST
        }
    }
}
