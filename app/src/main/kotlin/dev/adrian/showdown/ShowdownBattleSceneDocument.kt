package dev.adrian.showdown

/** Fills the placeholders of the battle scene page (`assets/showdown-battle-scene.html`). */
object ShowdownBattleSceneDocument {
    const val ASSET_NAME = "showdown-battle-scene.html"
    private const val PLAYBACK_SPEED_MINIMUM = "__PLAYBACK_SPEED_MINIMUM__"
    private const val PLAYBACK_SPEED_MAXIMUM = "__PLAYBACK_SPEED_MAXIMUM__"
    private const val PERSPECTIVE_GUARD = "__PERSPECTIVE_GUARD__"
    private const val DEBUG_FLAG = "__DEBUG__"

    fun render(template: String, debug: Boolean): String = template
        .replace(PLAYBACK_SPEED_MINIMUM, BattlePlaybackSpeed.MINIMUM.toString())
        .replace(PLAYBACK_SPEED_MAXIMUM, BattlePlaybackSpeed.MAXIMUM.toString())
        .replace(PERSPECTIVE_GUARD, ShowdownBattlePerspective.javascriptGuard)
        .replace(DEBUG_FLAG, debug.toString())
}
