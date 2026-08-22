package dev.adrian.showdown

object ShowdownTeamStatSummary {
    fun evs(values: List<Int>): String {
        val normalized = List(6) { values.getOrNull(it) ?: 0 }
        val total = normalized.sum()
        return if (total > EV_TOTAL_LIMIT || normalized.any { it !in 0..EV_MAX_PER_STAT }) {
            "EV total $total/$EV_TOTAL_LIMIT · over limit"
        } else {
            "EV total $total/$EV_TOTAL_LIMIT · ${EV_TOTAL_LIMIT - total} remaining"
        }
    }

    fun ivs(values: List<Int>): String {
        val normalized = List(6) { values.getOrNull(it) ?: IV_MAX_VALUE }
        val total = normalized.sum()
        return if (normalized.any { it !in 0..IV_MAX_VALUE }) {
            "IVs $total/$IV_TOTAL_LIMIT · invalid value"
        } else {
            "IVs $total/$IV_TOTAL_LIMIT · ${normalized.count { it == IV_MAX_VALUE }} perfect"
        }
    }

    private const val EV_MAX_PER_STAT = 252
    private const val EV_TOTAL_LIMIT = 510
    private const val IV_MAX_VALUE = 31
    private const val IV_TOTAL_LIMIT = IV_MAX_VALUE * 6
}
