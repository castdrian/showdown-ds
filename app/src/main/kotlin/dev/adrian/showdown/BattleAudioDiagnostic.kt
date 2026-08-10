package dev.adrian.showdown

data class BattleAudioCueEvent(
    val cue: BattleAudioCue,
    val queuedAtMillis: Long,
    val plannedDelayMillis: Long,
    val actualDelayMillis: Long,
    val playbackAccepted: Boolean
)

data class BattleAudioDiagnosticSnapshot(
    val loadedCues: Set<BattleAudioCue>,
    val failedCues: Set<BattleAudioCue>,
    val events: List<BattleAudioCueEvent>
) {
    val allSamplesLoaded: Boolean
        get() = loadedCues.containsAll(BattleAudioCue.values().toSet())

    val allPlaybackAccepted: Boolean
        get() = events.size == BattleAudioCue.values().size && events.all { it.playbackAccepted }

    val passed: Boolean
        get() = allSamplesLoaded && allPlaybackAccepted
}
