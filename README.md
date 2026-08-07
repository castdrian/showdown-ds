# showdown-ds

Dual Screen Pokémon Showdown! client for Android with a native Vulkan foundation and an AYN Thor-focused emulator profile.

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

The launcher uses `.emulator-overlay/` when present. The overlay contains the stock launcher and patched QEMU only, while linking the rest of the installed Android Emulator package. Build a local overlay from a synced Android Emulator source checkout with:

```sh
AEMU_SOURCE_ROOT=/path/to/aemu ./scripts/build-ayn-thor-emulator-overlay.sh
```

Build and install the debug app from Android Studio or with Gradle:

```sh
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## AYN Thor display profile

| Display | Resolution | Native density estimate | Refresh rate |
| --- | ---: | ---: | ---: |
| Top | 1920 × 1080 | 367 dpi | 120 Hz |
| Bottom | 1240 × 1080 | 420 dpi | 60 Hz |

The Google APIs profile configures the bottom display with the exact logical dimensions, density, touch capability, and Vulkan GPU path. The stock Android Emulator 37.1.11 renderer presents multiple displays beside one another in its combined host window. `tools/android-emulator/ayn-thor-single-window.patch` changes only the matching Thor display pair so a project-local emulator overlay places the lower display at a centered 340 px inset beneath the upper display. The overlay keeps regular Android, the stock emulator toolbar, and Extended Controls.

The tracked profile lives at `config/avd/ayn-thor.ini`. Generated emulator data stays under `.android/` and is ignored by Git.

## Native Vulkan foundation

The `app` module includes a CMake-built `showdown_vulkan` shared library. The starter activity creates a Vulkan instance and attaches Android surfaces from both the primary activity and the AYN Thor secondary-display presentation. Rendering systems can build on this surface registry without changing the Android project structure.
