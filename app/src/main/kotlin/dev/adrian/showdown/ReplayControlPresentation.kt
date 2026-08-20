package dev.adrian.showdown

internal object ReplayControlPresentation {
    val speeds = listOf(0.5f, 0.75f, 1f, 1.5f, 2f)

    fun pauseLabel(paused: Boolean) = if (paused) "Resume replay" else "Pause replay"

    fun statusLabel(paused: Boolean, speed: Float) = "${if (paused) "Paused" else "Playing"} · ${speedLabel(speed)}"

    fun speedLabel(speed: Float): String {
        val normalized = BattlePlaybackSpeed.coerce(speed)
        val text = if (normalized % 1f == 0f) normalized.toInt().toString() else normalized.toString()
        return "$text×"
    }
}
