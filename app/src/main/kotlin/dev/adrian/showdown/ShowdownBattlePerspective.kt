package dev.adrian.showdown

internal object ShowdownBattlePerspective {
    private val supportedSides = listOf("p1", "p2", "p3", "p4")

    fun acceptedSide(side: String) = side.takeIf(supportedSides::contains)

    val javascriptGuard = supportedSides.joinToString(" && ") { "side !== '$it'" }
}
