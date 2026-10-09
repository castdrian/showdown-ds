# Showdown!

Native Android Pokémon Showdown client for the AYN Thor dual-screen handheld.

<p align="center"><img width="49%" src="media/showdown-battle-live-both-sides.png?v=d6f1255af83ab215" alt="Live Gen 9 battle with both Pokémon visible, paired with its matching move-selection screen"> <img width="49%" src="media/showdown-battle-party-live-both-sides.png?v=48c152236d5a937e" alt="The same live battle with both Pokémon visible, paired with its matching six-Pokémon party screen"></p>

## Hardware target

- Upper display: 1920 × 1080 pixels, 152.4 mm diagonal, 120 Hz.
- Lower display: 1240 × 1080 pixels, 99.6 mm diagonal, 60 Hz.
- Closed enclosure: 150 × 94 × 25.6 mm, approximately 380 g.

The active panel areas are approximately 132.83 × 74.72 mm on top and 75.11 × 65.42 mm on the bottom, derived from the listed diagonals and native pixel aspect ratios.

## Build

```sh
./scripts/setup-android.sh
./scripts/create-ayn-thor-avd.sh
AEMU_SOURCE_ROOT=/path/to/aemu ./scripts/build-ayn-thor-emulator-overlay.sh
./scripts/run-ayn-thor-avd.sh
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Support the project

If this client is useful to you, [sponsor the project on GitHub](https://github.com/sponsors/castdrian) or [support it on Ko-fi](https://ko-fi.com/castdrian).

## Audio credits

The optional announcer clips come from [pbrchase](https://github.com/chfoo/pbrchase), using the English Pokémon Battle Revolution voice-track archive referenced by that project.
