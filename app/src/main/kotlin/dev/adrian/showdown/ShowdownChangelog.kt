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
                "Battle sound effects now pause, resume, and retire cleanly with the battle timeline.",
                "Gigantamax requests now honor Showdown's string capability flags.",
                "Official animated rear sprites are prioritized before lower-resolution fallbacks.",
                "Battle party indicators are cached between frames to reduce rendering work.",
                "Team exports now use Showdown's official packed advanced-field order, while older local exports remain readable.",
                "Finished battles keep the final Showdown result visible on the upper display.",
                "Replay controls now stay on the lower display with pause and 0.5×–2× speed selection.",
                "Replays identify their format and prioritize HD-first animated sprites whenever available.",
                "Search Showdown replays by player, opponent, or format and load a result directly into playback.",
                "Replay pagination now stays available below the result list while browsing.",
                "Battle animation, text pacing, and sound effects now share a synchronized 0.5×–2× speed range.",
                "Battle controls now include a 2× speed option.",
                "The optional PBR announcer now plays cues sequentially, follows battle speed, and pauses with the app.",
                "The optional PBR announcer now follows the native battle animation timeline for better move and hit timing.",
                "The optional PBR announcer now recognizes Gen 9 Snow weather as its winter weather callout.",
                "Stat-reset effects now use the matching rise or drop sound in the optional audio layer.",
                "Damage sound effects now follow observed HP damage and stay disarmed for non-damaging status moves.",
                "Native battle playback now recognizes ordinary unannotated move damage while keeping residual damage silent.",
                "Matchmaking now exposes an explicit Cancel search action while a queue is active.",
                "Battle animation uses less CPU, and finished battles release their hidden animation layer to reduce memory pressure.",
                "Animated HD sprite frames now use bounded display buffers to reduce Thor memory pressure.",
                "Sprite loading now keeps larger genuine HD animations while bounding their rendered frames and native decode budget.",
                "Large animated HD back sprites now remain eligible without increasing the rendered frame budget.",
                "The team editor now shows the live EV budget and perfect-IV count while you edit a set.",
                "Official No Item exports now round-trip without creating a fake item.",
                "Controller navigation now stays inside custom dialogs instead of triggering the battle menu underneath.",
                "Team previews keep species artwork visible while their preferred HD animation loads, including shiny variants.",
                "Team preview artwork now stays attached to the correct party slot while HD animations load or fall back.",
                "Battle room joins, leaves, and renames now keep the upper battle feed consistent with Showdown without duplicate entries.",
                "Terastallization now submits the official Showdown battle command and works correctly in live battles.",
                "Battle, ladder, and team format selectors now use searchable custom pickers for the full server catalog.",
                "Team selection now searches saved teams by name, format, folder, Pokémon, and moves.",
                "Room and live-battle catalogs now support custom search by room, player, and format.",
                "Tournament listings now support custom search by room, format, status, and player count.",
                "Ladder player lists now support public guest browsing and custom search by rank, name, and rating.",
                "Player lookup now works for guests through public Showdown profiles while private actions remain account-gated.",
                "Ladder players now open their public profile on selection, and room lists keep every visible row readable above the action bar."
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
