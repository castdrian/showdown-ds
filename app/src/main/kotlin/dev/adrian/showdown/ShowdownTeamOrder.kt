package dev.adrian.showdown

object ShowdownTeamOrder {
    fun <T> move(sets: List<T>, index: Int, direction: Int): List<T> {
        if (sets.size < 2 || index !in sets.indices || direction == 0) return sets
        val target = (index + direction).coerceIn(0, sets.lastIndex)
        if (target == index) return sets
        return sets.toMutableList().apply {
            add(target, removeAt(index))
        }
    }
}
