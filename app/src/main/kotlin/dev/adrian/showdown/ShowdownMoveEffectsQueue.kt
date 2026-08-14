package dev.adrian.showdown

import java.util.ArrayDeque

class ShowdownMoveEffectsQueue {
    sealed interface Packet {
        data class Seed(val lines: List<String>) : Packet
        data class Receive(
            val lines: List<String>,
            val battleLogGeneration: Long = 0L,
            val synchronizeBattleLog: Boolean = true
        ) : Packet
    }

    private val packets = ArrayDeque<Packet>()

    fun add(
        lines: List<String>,
        battleLogGeneration: Long = 0L,
        synchronizeBattleLog: Boolean = true
    ) {
        if (lines.isNotEmpty()) packets.addLast(Packet.Receive(lines, battleLogGeneration, synchronizeBattleLog))
    }

    fun resetWith(history: List<String>) {
        packets.clear()
        if (history.isNotEmpty()) packets.addLast(Packet.Seed(history))
    }

    fun clear() {
        packets.clear()
    }

    fun poll(): Packet? = if (packets.isEmpty()) null else packets.removeFirst()
}
