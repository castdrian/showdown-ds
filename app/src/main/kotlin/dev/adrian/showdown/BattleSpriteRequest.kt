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

data class BattleSceneSpriteRequests(
    val singlesBattle: Boolean,
    val playerLead: BattleSpriteRequest?,
    val opponentLead: BattleSpriteRequest?,
    val playerActive: List<BattleSpriteSlotRequest>,
    val opponentActive: List<BattleSpriteSlotRequest>
)

internal class BattleSpriteRequestTracker {
    private var previousRequests: BattleSceneSpriteRequests? = null

    fun updateIfChanged(requests: BattleSceneSpriteRequests): Boolean {
        if (requests == previousRequests) return false
        previousRequests = requests
        return true
    }

    fun reset() {
        previousRequests = null
    }
}

object BattleSpriteRequests {
    fun single(species: String, side: BattleSpriteSide, style: BattleSession.SpriteStyle, shiny: Boolean = false) =
        BattleSpriteRequest.forSide(species, side, style, shiny)

    fun forScene(
        playerCombatants: List<BattleSession.ActiveCombatant>,
        opponentCombatants: List<BattleSession.ActiveCombatant>,
        singlesBattle: Boolean,
        style: BattleSession.SpriteStyle,
        playerFallbackSpecies: String,
        opponentFallbackSpecies: String
    ): BattleSceneSpriteRequests {
        fun leadRequest(
            combatants: List<BattleSession.ActiveCombatant>,
            side: BattleSpriteSide,
            fallbackSpecies: String
        ): BattleSpriteRequest {
            val lead = combatants.firstOrNull()
            val species = lead?.species?.ifBlank { fallbackSpecies } ?: fallbackSpecies
            return single(species, side, style, lead?.shiny == true)
        }

        return BattleSceneSpriteRequests(
            singlesBattle = singlesBattle,
            playerLead = if (singlesBattle || playerCombatants.isEmpty()) {
                leadRequest(playerCombatants, BattleSpriteSide.PLAYER, playerFallbackSpecies)
            } else {
                null
            },
            opponentLead = if (singlesBattle || opponentCombatants.isEmpty()) {
                leadRequest(opponentCombatants, BattleSpriteSide.OPPONENT, opponentFallbackSpecies)
            } else {
                null
            },
            playerActive = if (singlesBattle) emptyList() else active(
                playerCombatants,
                BattleSpriteSide.PLAYER,
                style
            ),
            opponentActive = if (singlesBattle) emptyList() else active(
                opponentCombatants,
                BattleSpriteSide.OPPONENT,
                style
            )
        )
    }

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
