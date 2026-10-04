# Showdown!

Native Android Pokémon Showdown client for the AYN Thor dual-screen handheld.

<table>
  <tr>
    <td width="50%"><img src="media/showdown-battle-hd-both-sides.png?v=dc7e959ef7a64013" alt="Live Gen 9 battle with Duraludon and Arcanine above its matching move-choice screen"></td>
    <td width="50%"><img src="media/showdown-battle-party-both-sides.png?v=2db153e83258ea67" alt="The same live matchup above its matching Pokémon party screen"></td>
  </tr>
</table>

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
