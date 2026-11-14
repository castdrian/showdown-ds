package dev.adrian.showdown

/** Text shown when offering to resume or forfeit an unfinished battle. */
object ShowdownUnfinishedBattlePresentation {
    const val LIST_TITLE = "Unfinished battles"
    const val ACTIONS_TITLE = "Unfinished battle"
    const val RESUME_BUTTON = "Resume"
    const val FORFEIT_BUTTON = "Forfeit"
    const val LATER_BUTTON = "Later"
    const val CANCEL_BUTTON = "Cancel"
    const val FORFEIT_CONFIRM_TITLE = "Forfeit this battle?"
    const val FORFEIT_REQUESTED_STATUS = "Forfeit requested."
    const val FORFEIT_FAILED_STATUS = "Unable to forfeit that battle."
    const val RESUME_UNAVAILABLE_STATUS = "Connect to Showdown before resuming a battle."
    const val NONE_STATUS = "There are no unfinished battles."
    private const val ACTIONS_HINT = "Resume to rejoin it, or forfeit to concede."

    fun label(entry: ShowdownUnfinishedBattles.Entry): String = "${entry.title}\n${entry.format}"

    fun actionsMessage(entry: ShowdownUnfinishedBattles.Entry): String = "${label(entry)}\n\n$ACTIONS_HINT"
}
