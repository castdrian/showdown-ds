# Showdown!

Dual Screen Pokémon Showdown! client for Android with a native Vulkan foundation and an AYN Thor-focused emulator profile.

The top display is a full battle scene with Showdown CDN Pokémon and trainer sprites, Showdown’s own battle-animation mapping in a transparent effects layer, randomized Showdown backdrops and music, a live battle feed, and tap-to-toggle Pokémon detail sheets. The lower display is a touch-first command deck for battle decisions, team selection, chat, and battle history. Tapping a move queues it immediately; optional touch confirmation is available in the X-menu. Its menus are also navigable with Android gamepad events: D-pad or left stick navigates, A selects, B returns, L/R cycle panels, X opens the menu, Y opens the team, L2 opens chat, and R2 opens the log.

The app defaults to Showdown’s animated XY sprites and caches CDN resources in memory plus an internal 96 MB least-recently-used disk cache. It resolves live move and Pokémon types from the official Showdown dex files, uses species cries, and starts each bundled Gen 7 move sound on the same official animation callback that begins its visual effect. When a battle sprite is still loading, the upper renderer uses Showdown’s animated Substitute placeholder instead of blocking battle input.

## Preview

<video controls playsinline preload="metadata" width="480" poster="https://raw.githubusercontent.com/castdrian/showdown-ds/main/media/showdown-promo-poster.jpg">
  <source src="https://raw.githubusercontent.com/castdrian/showdown-ds/main/media/showdown-promo.mp4" type="video/mp4">
</video>

[Open the 30-second dual-screen preview](media/showdown-promo.mp4)

## Android setup

The project targets Android 14 for development while retaining Android 13 compatibility, which matches the operating system shipped on the AYN Thor.

Install the Android SDK components required by the project:

```sh
./scripts/setup-android.sh
```

Create the repo-local AVD:

```sh
./scripts/create-ayn-thor-avd.sh
```

Launch it with host GPU rendering:

```sh
./scripts/run-ayn-thor-avd.sh
```

The development launcher defaults to 60 Hz, which keeps the dual-display Vulkan setup responsive on passively cooled Apple Silicon Macs. Use the hardware-rate validation mode when needed:

```sh
AYN_THOR_VSYNC_RATE=120 ./scripts/run-ayn-thor-avd.sh
```

It also skips the Android boot animation by default; set `AYN_THOR_BOOT_ANIMATION=1` to retain it. Host-Vulkan emulator sessions may require cold boots because Vulkan snapshots are unsupported, so a full startup after a graphics, AVD, or emulator update is expected.

The launcher uses `.emulator-overlay/` when present. The overlay contains the stock launcher and patched QEMU only, while linking the rest of the installed Android Emulator package. Build a local overlay from a synced Android Emulator source checkout with:

```sh
AEMU_SOURCE_ROOT=/path/to/aemu ./scripts/build-ayn-thor-emulator-overlay.sh
```

Build and install the debug app from Android Studio or with Gradle:

```sh
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Releases and Obtainium

Tag prereleases as `vMAJOR.MINOR.PATCH-alpha.N` or `vMAJOR.MINOR.PATCH-beta.N`; select the matching workflow channel and GitHub marks them as prereleases. Tag stable releases as `vMAJOR.MINOR.PATCH` and select stable. The release workflow derives the Android version name and an increasing version code from the numeric tag, builds the corresponding `Showdown-*.apk`, and attaches it to the matching GitHub Release. Obtainium can track this repository with the GitHub release source and install the APK asset directly.

Before publishing the first release, add one permanent signing key to the repository secrets: `SHOWDOWN_KEYSTORE_BASE64`, `SHOWDOWN_KEYSTORE_PASSWORD`, `SHOWDOWN_KEY_ALIAS`, and `SHOWDOWN_KEY_PASSWORD`. The workflow requires them so every Obtainium update has the same signing certificate.

`Android CI` runs the official battle transcript, battle-protocol contract, server endpoint, and Showdown asset-path suites before the full unit suite and debug APK build. Its JUnit XML and HTML reports are attached to every run.

## AYN Thor display profile

| Display | Resolution | Native density estimate | Refresh rate |
| --- | ---: | ---: | ---: |
| Top | 1920 × 1080 | 367 dpi | 120 Hz hardware target |
| Bottom | 1240 × 1080 | 420 dpi | 120 Hz hardware target |

The Google APIs profile configures the bottom display with the exact logical dimensions, density, touch capability, and Vulkan GPU path. The stock Android Emulator 37.1.11 renderer presents multiple displays beside one another in its combined host window. `tools/android-emulator/ayn-thor-single-window.patch` changes only the matching Thor display pair so a project-local emulator overlay places the lower display at a centered 340 px inset beneath the upper display. The overlay keeps regular Android, the stock emulator toolbar, and Extended Controls.

The tracked profile lives at `config/avd/ayn-thor.ini`. Generated emulator data stays under `.android/` and is ignored by Git.

## Emulator overlay artifacts

`Build latest AYN Thor emulator overlay` is a scheduled and manually dispatchable workflow for a self-hosted Apple Silicon macOS runner. It syncs the requested Android Emulator source branch, applies the tracked AYN Thor display-layout patch, and uploads the patched QEMU overlay binary. Download its `ayn-thor-emulator-overlay-darwin-aarch64` artifact and pass its `qemu-system-aarch64` binary to `scripts/install-ayn-thor-emulator-overlay.sh`.

The workflow uses a self-hosted runner because a complete Android Emulator source checkout and native build need substantially more disk than a standard hosted runner provides.

## Native Vulkan foundation

The `app` module includes a CMake-built `showdown_vulkan` shared library. The starter activity creates a Vulkan instance and attaches Android surfaces from both the primary activity and the AYN Thor secondary-display presentation. Rendering systems can build on this surface registry without changing the Android project structure.
