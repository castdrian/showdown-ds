package dev.adrian.showdown

data class ShowdownRoomPacket(val roomId: String?, val lines: List<String>)

object ShowdownProtocolPackets {
    fun decode(message: String): List<ShowdownRoomPacket> {
        val packets = mutableListOf<ShowdownRoomPacket>()
        var roomId: String? = null
        var lines = mutableListOf<String>()

        fun appendPacket() {
            if (lines.isEmpty()) return
            packets += ShowdownRoomPacket(roomId, lines.toList())
            lines = mutableListOf()
        }

        message.lineSequence().forEach { line ->
            if (line.startsWith(">")) {
                appendPacket()
                roomId = line.drop(1).ifBlank { null }
            } else if (line.isNotEmpty()) {
                lines += line
            }
        }

        appendPacket()
        return packets
    }
}
