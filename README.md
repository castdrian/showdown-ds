# Showdown!

Native Android Pokémon Showdown client for the AYN Thor dual-screen handheld.

<p align="center"><img width="49%" src="media/showdown-battle-live-both-sides.png?v=1afa7a1239302d36" alt="Live Gen 9 battle with both Pokémon visible, paired with its matching move-selection screen"> <img width="49%" src="media/showdown-battle-party-live-both-sides.png?v=486dcecbe60d0d8b" alt="The same live battle with both Pokémon visible, paired with its matching six-Pokémon party screen"></p>

## Hardware target

- Upper display: 1920 × 1080 pixels, 152.4 mm diagonal, 120 Hz.
- Lower display: 1240 × 1080 pixels, 99.6 mm diagonal, 60 Hz.
- Closed enclosure: 150 × 94 × 25.6 mm, approximately 380 g.

The active panel areas are approximately 132.83 × 74.72 mm on top and 75.11 × 65.42 mm on the bottom, derived from the listed diagonals and native pixel aspect ratios.

## How it works

During a battle the upper screen is the Pokémon Showdown web client's battle scene, rendered in a WebView, and the lower screen holds the native controls (moves, party, activity log, menu). The Pokémon tab shows both teams with HP, held item, types, status and stat boosts. Outside battles the upper screen shows a lobby card. If your account has unfinished battles, the app offers to resume or forfeit them instead of reopening them.

## Build

Requires JDK 17 and the Android SDK (`./scripts/setup-android.sh` installs what is needed).

```sh
./build.sh                    # debug APK
./build.sh --test             # build, then lint and run the unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Develop without the hardware

`./emulate.sh` starts the AYN Thor emulator profile, builds the `local` variant (its own app id, data and saved login, defaulting to a local Showdown server) and installs it. The first run needs the emulator profile:

```sh
./scripts/setup-android.sh
./scripts/create-ayn-thor-avd.sh
AEMU_SOURCE_ROOT=/path/to/aemu ./scripts/build-ayn-thor-emulator-overlay.sh   # patched emulator with the stacked Thor screens
./emulate.sh
```

Without the patched emulator, `emulate.sh` falls back to the stock emulator (both displays exist, but their arrangement is not guaranteed).

Test against a local server instead of the real one:

```sh
./scripts/local-showdown.sh                                          # server on port 8000, guest logins
node tools/local-showdown/bot.ts --mode search                       # bot opponent (Node 22.18+)
./emulate.sh --login LocalUser                                       # sign the local build in as a named user
```

`--stall-after-turn N` makes the bot stop answering so a battle stays unfinished, which is how to exercise Resume and Forfeit. Instrumented tests run against the local build: `./gradlew :app:connectedLocalAndroidTest`.

## Support the project

If this client is useful to you, [sponsor the project on GitHub](https://github.com/sponsors/castdrian) or [support it on Ko-fi](https://ko-fi.com/castdrian).

## Audio credits

The optional announcer clips come from [pbrchase](https://github.com/chfoo/pbrchase), using the English Pokémon Battle Revolution voice-track archive referenced by that project.
