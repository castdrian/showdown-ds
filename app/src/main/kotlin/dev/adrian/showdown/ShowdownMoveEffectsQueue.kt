package dev.adrian.showdown

import java.util.ArrayDeque

class ShowdownMoveEffectsQueue {
    sealed interface Packet {
        data class Seed(val lines: List<String>, val effectsBarrierToken: Long = 0L) : Packet
        data class Receive(
            val lines: List<String>,
            val battleLogGeneration: Long = 0L,
            val synchronizeBattleLog: Boolean = true,
            val effectsBarrierToken: Long = 0L
        ) : Packet
    }

    private val packets = ArrayDeque<Packet>()

    fun add(
        lines: List<String>,
        battleLogGeneration: Long = 0L,
        synchronizeBattleLog: Boolean = true,
        effectsBarrierToken: Long = 0L
    ) {
        if (lines.isNotEmpty()) packets.addLast(
            Packet.Receive(lines, battleLogGeneration, synchronizeBattleLog, effectsBarrierToken)
        )
    }

    fun resetWith(history: List<String>, effectsBarrierToken: Long = 0L) {
        packets.clear()
        if (history.isNotEmpty()) packets.addLast(Packet.Seed(history, effectsBarrierToken))
    }

    fun clear() {
        packets.clear()
    }

    fun poll(): Packet? = if (packets.isEmpty()) null else packets.removeFirst()
}
