package dev.adrian.showdown

object BattleCombatantLayout {
    fun centeredSlot(
        triplesCentered: Boolean,
        combatants: List<BattleSession.ActiveCombatant>
    ): String? = if (triplesCentered) {
        combatants.singleOrNull { !it.condition.contains("FNT", true) }?.slot
    } else {
        null
    }

    fun x(
        width: Float,
        player: Boolean,
        index: Int,
        count: Int,
        centeredSlot: String? = null,
        slot: String? = null
    ): Float {
        val base = if (player) 0.20f else 0.64f
        val step = if (count > 2) 0.12f else 0.16f
        val position = if (centeredSlot != null && centeredSlot == slot) {
            base + step * (count - 1) / 2f
        } else {
            base + index * step
        }
        return width * position
    }
}
