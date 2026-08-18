package dev.adrian.showdown

enum class BattleSpriteSide {
    PLAYER,
    OPPONENT
}

data class BattleSpriteRequest(
    val species: String,
    val side: BattleSpriteSide,
    val style: BattleSession.SpriteStyle,
    val shiny: Boolean = false
) {
    val backFacing get() = side == BattleSpriteSide.PLAYER

    companion object {
        fun forPlayer(species: String, style: BattleSession.SpriteStyle, shiny: Boolean = false) =
            BattleSpriteRequest(species, BattleSpriteSide.PLAYER, style, shiny)

        fun forOpponent(species: String, style: BattleSession.SpriteStyle, shiny: Boolean = false) =
            BattleSpriteRequest(species, BattleSpriteSide.OPPONENT, style, shiny)

        fun forSide(species: String, side: BattleSpriteSide, style: BattleSession.SpriteStyle, shiny: Boolean = false) =
            BattleSpriteRequest(species, side, style, shiny)
    }
}

data class BattleSpriteSlotRequest(
    val slot: String,
    val request: BattleSpriteRequest
)

object BattleSpriteRequests {
    fun single(species: String, side: BattleSpriteSide, style: BattleSession.SpriteStyle, shiny: Boolean = false) =
        BattleSpriteRequest.forSide(species, side, style, shiny)

    fun active(
        combatants: List<BattleSession.ActiveCombatant>,
        side: BattleSpriteSide,
        style: BattleSession.SpriteStyle
    ) = combatants.map { combatant ->
        BattleSpriteSlotRequest(
            combatant.slot,
            BattleSpriteRequest.forSide(combatant.species.ifBlank { combatant.name }, side, style, combatant.shiny)
        )
    }
}
