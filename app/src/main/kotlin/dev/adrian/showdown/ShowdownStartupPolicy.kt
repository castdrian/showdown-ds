package dev.adrian.showdown

object ShowdownStartupPolicy {
    fun shouldConnectToLobby(
        restoredConnection: Boolean,
        incomingIntentHandled: Boolean,
        replaySource: String?
    ): Boolean = !restoredConnection && !incomingIntentHandled && replaySource.isNullOrBlank()
}
