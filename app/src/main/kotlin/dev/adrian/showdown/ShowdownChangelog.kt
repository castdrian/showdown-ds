package dev.adrian.showdown

data class ShowdownChangelogEntry(
    val version: String,
    val changes: List<String>
)

object ShowdownChangelog {
    fun entries(currentVersion: String) = listOf(
        ShowdownChangelogEntry(
            currentVersion,
            listOf(
                "Account settings can now create a registered Showdown account and finish sign-in without leaving the client.",
                "Battle sound effects now stop cleanly when the app leaves the foreground instead of resuming out of sync.",
                "Move detail panels now focus on readable move names, power, and accuracy while selector cards retain PP.",
                "Move previews now fill power, accuracy, category, and gimmick power from the official dex while keeping status and always-hit values as dashes.",
                "Type icons remain on move selectors without repeating the type label in the card or detail panel.",
                "Battle audio cues now wait for the previous clip to finish, keeping damage and effectiveness sounds synchronized.",
                "Battle audio timing resets at each move boundary so faster playback cannot delay the next move's cue.",
                "Damage cues also follow health changes represented by set-HP packets without sounding for healing.",
                "Battle challenges announced by Showdown now open the custom accept or reject flow.",
                "Battle playback, audio cues, and the two-screen presentation remain tuned for human-speed reading.",
                "Matched battles are recovered after reconnecting, even when the room ID stays the same.",
                "Battle form-change packets now keep the visible HP and status cards in sync."
            )
        ),
        ShowdownChangelogEntry(
            "v0.1.0-alpha.3",
            listOf(
                "Added an in-app What's new changelog to the Info & resources screen.",
                "Release APK assets now use the lowercase showdown- filename prefix.",
                "Improved challenge notifications and private-message handling."
            )
        ),
        ShowdownChangelogEntry(
            "v0.1.0-alpha.2",
            listOf(
                "Added a signed installable alpha release for Android.",
                "Added adjustable replay speed.",
                "Refined the custom Thor UI, battle log contrast, and readable battle information."
            )
        ),
        ShowdownChangelogEntry(
            "v0.1.0-alpha.1",
            listOf(
                "Added live Showdown battles, team building, replays, rooms, chat, and account tools.",
                "Added dual-screen battle presentation with touch and controller support."
            )
        )
    )
}
