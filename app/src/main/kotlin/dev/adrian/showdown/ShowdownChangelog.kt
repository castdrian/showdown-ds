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
                "Battle sound effects now pause and resume with their active move timing when the app leaves and re-enters the foreground.",
                "Live battle animations now pause with the app and resume cleanly after returning to the foreground.",
                "Move detail panels now use matching padded metric cards for large, readable power, accuracy, category, and target values while selector cards retain PP.",
                "Move previews now fill power, accuracy, category, and gimmick power from the official dex while keeping status and always-hit values as dashes.",
                "Type icons remain on move selectors without repeating the type label in the card or detail panel.",
                "Impact audio now starts with the damage animation instead of waiting behind longer status clips, with effectiveness cues following the hit.",
                "Battle audio timing resets at each move boundary so faster playback cannot delay the next move's cue.",
                "Damage cues also follow health changes represented by set-HP packets without sounding for healing.",
                "Dynamax and Terastal choices now use the official Showdown command suffixes.",
                "Consumed items now disappear from Pokémon details and appear in the battle log.",
                "Battle requests no longer show a Terastal action when the server explicitly disables it.",
                "Doubles item and ability updates now stay on the correct active Pokémon.",
                "Battle details now resolve nickname-based protocol identifiers to the active species.",
                "Active Pokémon details now track readable temporary battle effects until Showdown ends them.",
                "Battle inspections now distinguish turn-scoped protections from move-scoped effects and clear them on the same boundaries as Showdown.",
                "Protection blocks and Roost now update active battle state and displayed types like Showdown.",
                "Ability activations now reveal the correct ability in the active Pokémon details.",
                "Internal ability boost tokens no longer appear as unreadable battle-feed text.",
                "Duplicate species with different nicknames now keep separate party details.",
                "Duplicate opponent species now keep item, status, and form updates on the correct party member.",
                "Long multi-part form identifiers such as Alcremie-Caramel-Swirl now use the base species in readable battle labels while retaining the full form for sprites and battle state.",
                "Battle challenges announced by Showdown now open the custom accept or reject flow.",
                "Battle playback, audio cues, and the two-screen presentation remain tuned for human-speed reading.",
                "The first live battle state is no longer fed into the animation layer twice, preventing duplicated opening effects and audio timing drift.",
                "Live battle choices now remain queued until Showdown acknowledges them, including across reconnects and activity recreation.",
                "Doubles and triples target selection now follows Showdown's adjacent, ally, foe, and area-target rules.",
                "Matched battles are recovered after reconnecting, even when the room ID stays the same.",
                "Battle form-change packets now keep the visible HP and status cards in sync.",
                "Mega and Primal battle packets now keep their revealed stone or orb visible in Pokémon details.",
                "Fresh installs now default to the current Gen 9 Random Battle format.",
                "Team JSON import and export now preserve Showdown's advanced set fields.",
                "Team imports now also accept the beta client's bracketed ability and inline nature format.",
                "Remote team imports now keep every Pokémon from the selected Showdown export.",
                "Custom dialogs no longer open the keyboard automatically, keeping their actions visible until an input is tapped.",
                "Iron Valiant now uses Showdown's native Gen 5 back sprite on the player side, with a verified back-sprite fallback.",
                "The secondary Thor display keeps controller focus available for touch and hardware input.",
                "Lower-screen controls now size themselves from the Thor lower panel's actual display density instead of inheriting the upper-screen metrics.",
                "Unknown move records no longer arm the generic damage cue, preventing sound effects from being attached to unclassified animations."
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
