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
                "Battle format names now use Showdown's official [Gen 9] Random Battle label.",
                "Battle messages now appear as complete, readable lines with a gentle fade between events.",
                "Damage, effectiveness, and stat-change sounds now follow the matching battle animation.",
                "High-resolution artwork is preferred for battle and team previews, with correct rear-facing fallbacks.",
                "Animated sprite fallbacks now replace static artwork even when they finish loading later.",
                "The AYN Thor presentation keeps the large battle screen above the compact control screen with reliable touch input.",
                "Singles, doubles, and triples now share compact HP cards and readable switch screens.",
                "Live matchmaking, reconnects, replay playback, chat, rooms, and account sessions are more resilient.",
                "Team Builder now supports Showdown formats, four searchable moves, EVs, IVs, and packed or text imports and exports.",
                "Team Builder suggestions now follow the selected Pokémon's official abilities and learnset."
            )
        ),
        ShowdownChangelogEntry(
            "v0.1.0-alpha.3",
            listOf(
                "Added the in-app What's new screen.",
                "Release APK assets now use the lowercase showdown- filename prefix.",
                "Improved challenge notifications and private-message handling."
            )
        ),
        ShowdownChangelogEntry(
            "v0.1.0-alpha.2",
            listOf(
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
