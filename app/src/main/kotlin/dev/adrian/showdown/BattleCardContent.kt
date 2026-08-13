package dev.adrian.showdown

data class BattleCardContent(
    val title: String,
    val levelLabel: String,
    val hpLabel: String,
    val fraction: Float
) {
    companion object {
        fun from(details: BattleSession.PokemonDetails) = from(
            name = details.name,
            species = details.species,
            level = details.level,
            gender = details.gender,
            hp = details.hp
        )

        fun from(details: BattleSession.PokemonDetails, hp: String) = from(
            name = details.name,
            species = details.species,
            level = details.level,
            gender = details.gender,
            hp = hp
        )

        fun from(combatant: BattleSession.ActiveCombatant) = from(
            name = combatant.name,
            species = combatant.species,
            level = combatant.level,
            gender = combatant.gender,
            hp = combatant.hp
        )

        fun from(name: String, species: String, level: String, gender: String, hp: String) = BattleCardContent(
            title = BattleSession.displayPokemonName(name, species),
            levelLabel = "Lv.$level$gender",
            hpLabel = hp.substringBefore(' '),
            fraction = fraction(hp)
        )

        private fun fraction(hp: String): Float {
            val value = hp.substringBefore(' ')
            if (hp.contains("fnt", true)) return 0f
            if (value.endsWith('%')) return (value.dropLast(1).toFloatOrNull()?.div(100f) ?: 1f).coerceIn(0f, 1f)
            val values = value.split('/', limit = 2)
            val current = values.getOrNull(0)?.toFloatOrNull() ?: return 1f
            val maximum = values.getOrNull(1)?.toFloatOrNull() ?: return 1f
            return if (maximum > 0f) (current / maximum).coerceIn(0f, 1f) else 0f
        }
    }
}
