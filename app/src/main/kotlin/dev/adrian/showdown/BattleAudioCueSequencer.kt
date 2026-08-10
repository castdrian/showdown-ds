package dev.adrian.showdown

import java.util.ArrayDeque

class BattleAudioCueSequencer(
    private val listener: (BattleAudioCue) -> Unit
) {
    private val pendingEffectiveness = ArrayDeque<BattleAudioCue>()
    private var damagePlayed = false

    @Synchronized
    fun reset() {
        pendingEffectiveness.clear()
        damagePlayed = false
    }

    @Synchronized
    fun beginMove() {
        reset()
    }

    @Synchronized
    fun receive(cue: BattleAudioCue) {
        when (cue) {
            BattleAudioCue.GENERIC_DAMAGE -> {
                if (damagePlayed) return
                damagePlayed = true
                listener(cue)
                while (pendingEffectiveness.isNotEmpty()) listener(pendingEffectiveness.removeFirst())
            }
            BattleAudioCue.SUPER_EFFECTIVE,
            BattleAudioCue.NOT_VERY_EFFECTIVE -> {
                if (damagePlayed) listener(cue) else pendingEffectiveness.addLast(cue)
            }
            else -> listener(cue)
        }
    }
}
