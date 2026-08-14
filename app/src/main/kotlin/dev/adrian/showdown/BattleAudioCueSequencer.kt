package dev.adrian.showdown

class BattleAudioCueSequencer(
    private val listener: (BattleAudioCue) -> Unit
) {
    private var pendingEffectiveness: BattleAudioCue? = null
    private var effectivenessPlayed = false
    private var damagePlayed = false

    @Synchronized
    fun reset() {
        pendingEffectiveness = null
        effectivenessPlayed = false
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
                pendingEffectiveness?.let {
                    listener(it)
                    pendingEffectiveness = null
                    effectivenessPlayed = true
                }
            }
            BattleAudioCue.SUPER_EFFECTIVE,
            BattleAudioCue.NOT_VERY_EFFECTIVE -> {
                if (effectivenessPlayed) return
                if (damagePlayed) {
                    listener(cue)
                    effectivenessPlayed = true
                } else if (pendingEffectiveness == null) {
                    pendingEffectiveness = cue
                }
            }
            else -> listener(cue)
        }
    }
}
