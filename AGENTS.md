# Agent instructions

Native Android Pokémon Showdown client for the AYN Thor dual-screen handheld (Kotlin, one Gradle module `:app`, package `dev.adrian.showdown`).

## Build, test, run

| Task | Command |
| --- | --- |
| Build (debug) | `./build.sh` |
| Build + lint + all tests | `./build.sh --test` (Android Lint, unit tests, Go tool tests if Go is installed) |
| Build in Docker | `docker build -t showdown-ds . && docker run --rm showdown-ds` |
| JVM unit tests only | `./gradlew :app:testDebugUnitTest` |
| Instrumented tests (emulator) | `./gradlew :app:connectedLocalAndroidTest` (runs against the `local` build, never the `.debug` install) |
| Emulator + install + launch | `./emulate.sh` (default `local` variant) |
| Local Showdown server | `./scripts/local-showdown.sh` (port 8000, guest logins) |
| Bot opponent | `node tools/local-showdown/bot.ts --mode search` (Node 22.18+; `--stall-after-turn N` leaves a battle unfinished) |
| Named dev user | `./emulate.sh --login LocalUser` (local variant only) |

Needs JDK 17, the Android SDK (`./scripts/setup-android.sh`) and, for the Go tooling tests, Go 1.24.

## Build variants

- `debug` (`dev.adrian.showdown.debug`): talks to the official server by default and keeps the developer's saved login.
- `local` (`dev.adrian.showdown.local`): own app id and data; defaults to the local server at `ws://10.0.2.2:8000`. Use this for all automated and agent testing. Driving the lower screen over adb: `adb shell input -d 2 tap X Y` (display 2), after tapping it once so it holds focus for `uiautomator dump`.
- `release`: signed from `SHOWDOWN_*` environment variables.

## Rules for agents

- **Never use the developer's Showdown account.** Do not log in as them, send commands on their behalf, forfeit their battles, or run `pm clear` on `dev.adrian.showdown.debug`. Test against the local server with the `local` variant.
- Tests come first and must test behavior; do not edit a test just to make it pass. No mocks without asking. Name tests `Thing_Scenario_Expected`.
- The in-battle top screen is a WebView running Showdown's own client; do not test behavior inside it, only that the view is created and loads.
- Log work in `context/` (see `context/README.md`) and record odd app behavior in `context/inconsistencies.md`.

## Layout

- `app/src/main/kotlin/dev/adrian/showdown/`: app code. `MainActivity` wires everything; `BattleSession` is the battle state machine; `CommandDeckView` is the lower-screen UI; `ShowdownMoveEffectsView` hosts the Showdown client WebView (page in `app/src/main/assets/showdown-battle-scene.html`); `LobbyView` draws the upper screen outside battles.
- `app/src/test`: JVM tests (replay fixtures in `test/resources/showdown-replays`). `app/src/androidTest`: instrumented tests (`testBuildType` is `local`). `app/src/local`: the log-parity harness activity used by them.
- `scripts/`, `tools/`: emulator profile, local server, Go helpers for assets and README media.
- `context/`: working notes, decisions and known inconsistencies.

## Gotchas

- Emulator host networking: the emulator reaches the host's loopback at `10.0.2.2`.
- `run-ayn-thor-avd.sh` needs the patched emulator overlay; `emulate.sh` sets `AYN_THOR_ALLOW_STOCK_EMULATOR=1` so the stock emulator works (screen order not guaranteed).
- Linux without `libpulse0`: unpack the package locally and add it to `LD_LIBRARY_PATH` (see `context/environment.md`).
- Showdown's client CSS and JS load live from `play.pokemonshowdown.com`, so upstream changes can change the in-battle look.
