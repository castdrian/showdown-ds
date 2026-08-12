package dev.adrian.showdown

enum class BattleSpriteSide {
    PLAYER,
    OPPONENT
}

data class BattleSpriteRequest(
    val species: String,
    val side: BattleSpriteSide,
    val style: BattleSession.SpriteStyle
) {
    val backFacing get() = side == BattleSpriteSide.PLAYER

    companion object {
        fun forPlayer(species: String, style: BattleSession.SpriteStyle) =
            BattleSpriteRequest(species, BattleSpriteSide.PLAYER, style)

        fun forOpponent(species: String, style: BattleSession.SpriteStyle) =
            BattleSpriteRequest(species, BattleSpriteSide.OPPONENT, style)

        fun forSide(species: String, side: BattleSpriteSide, style: BattleSession.SpriteStyle) =
            BattleSpriteRequest(species, side, style)
    }
}

data class BattleSpriteSlotRequest(
    val slot: String,
    val request: BattleSpriteRequest
)

object BattleSpriteRequests {
    fun single(species: String, side: BattleSpriteSide, style: BattleSession.SpriteStyle) =
        BattleSpriteRequest.forSide(species, side, style)

    fun active(
        combatants: List<BattleSession.ActiveCombatant>,
        side: BattleSpriteSide,
        style: BattleSession.SpriteStyle
    ) = combatants.map { combatant ->
        BattleSpriteSlotRequest(
            combatant.slot,
            BattleSpriteRequest.forSide(combatant.species.ifBlank { combatant.name }, side, style)
        )
    }
}
