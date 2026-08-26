package dev.adrian.showdown

object ShowdownBattleMovePresentation {
    fun animationName(moveArguments: List<String>, displayedMoveName: String): String {
        moveArguments.map(String::trim).forEach { argument ->
            if (!argument.startsWith("[anim]", true)) return@forEach
            val inline = argument.substringAfter(']', "").trim()
            if (inline.isNotBlank()) return inline
        }
        return displayedMoveName
    }
}
