package dev.adrian.showdown

object ShowdownBattleMovePresentation {
    data class ProtocolAnimation(
        val actor: String,
        val moveName: String,
        val target: String?,
        val shouldAnimate: Boolean
    )

    fun shouldAnimate(moveArguments: List<String>): Boolean = moveArguments.none {
        it.trim().equals("[still]", true)
    }

    fun protocolAnimation(fields: List<String>): ProtocolAnimation? {
        if (fields.getOrNull(1) != "-anim") return null
        val actor = fields.getOrNull(2)?.takeIf(String::isNotBlank) ?: return null
        val moveName = fields.getOrNull(3)?.takeIf(String::isNotBlank) ?: return null
        val target = fields.getOrNull(4)?.takeIf { it.isNotBlank() && it.contains(":") }
        val arguments = fields.drop(5)
        return ProtocolAnimation(actor, moveName, target, shouldAnimate(arguments))
    }

    fun animationName(moveArguments: List<String>, displayedMoveName: String): String {
        moveArguments.map(String::trim).forEach { argument ->
            if (!argument.startsWith("[anim]", true)) return@forEach
            val inline = argument.substringAfter(']', "").trim()
            if (inline.isNotBlank()) return inline
        }
        return displayedMoveName
    }
}
