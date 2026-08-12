package dev.adrian.showdown

class BattleDisplayRefreshScheduler(
    private val schedule: (Runnable) -> Unit,
    private val refresh: () -> Unit
) {
    private var scheduled = false
    private var generation = 0

    fun request() {
        if (scheduled) return
        scheduled = true
        val requestGeneration = ++generation
        schedule(Runnable {
            if (!scheduled || requestGeneration != generation) return@Runnable
            scheduled = false
            refresh()
        })
    }

    fun cancel() {
        scheduled = false
        generation += 1
    }
}
