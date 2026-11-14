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

## Sign and publish a release APK

Android only installs signed APKs. Debug builds use an automatic debug key; release builds need a key you create once and keep. Updates only install over an existing app if they are signed with the same key, so back the keystore and its passwords up and never commit them.

1. Create the keystore (keep it outside the repository):

   ```sh
   keytool -genkeypair -v -keystore showdown-release.jks -alias showdown \
     -keyalg RSA -keysize 2048 -validity 10000
   base64 -w0 showdown-release.jks > keystore.b64
   ```

2. Add four GitHub Actions secrets (Settings > Secrets and variables > Actions):

   ```sh
   gh secret set SHOWDOWN_KEYSTORE_BASE64 < keystore.b64
   gh secret set SHOWDOWN_KEYSTORE_PASSWORD
   gh secret set SHOWDOWN_KEY_ALIAS --body showdown
   gh secret set SHOWDOWN_KEY_PASSWORD
   ```

3. Push a version tag: `vX.Y.Z` (stable release) or `vX.Y.Z-alpha.N` / `vX.Y.Z-beta.N` (pre-release). The `Android release package` workflow builds the signed APK and attaches `showdown-<tag>.apk` to a GitHub release, with notes generated from the conventional commits since the previous tag.

   ```sh
   git tag v0.2.0-alpha.1 && git push origin v0.2.0-alpha.1
   ```

To build a signed APK locally instead, export `SHOWDOWN_KEYSTORE_PATH`, `SHOWDOWN_KEYSTORE_PASSWORD`, `SHOWDOWN_KEY_ALIAS` and `SHOWDOWN_KEY_PASSWORD` and run `./gradlew :app:assembleRelease`. Verify a result with `apksigner verify --print-certs <apk>`.

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
