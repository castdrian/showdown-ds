# Showdown!

Native Android Pokémon Showdown client for the AYN Thor dual-screen handheld.

https://github.com/user-attachments/assets/84c91f48-0431-45da-9f18-787504814ba6

## Hardware target

- Upper display: 1920 × 1080 pixels, 6 inches, 120 Hz.
- Lower display: 1240 × 1080 pixels, 3.92 inches, 60 Hz.
- Closed enclosure: 150 × 94 × 25.6 mm, approximately 380 g.

The repository AVD uses the two display resolutions and densities from the Thor target. The published hardware specifications are summarized by [Android Central](https://www.androidcentral.com/gaming/android-games/ayn-thor-pre-orders-open-tonight-and-its-much-cheaper-than-i-thought).

## Build

```sh
./scripts/setup-android.sh
./scripts/create-ayn-thor-avd.sh
./scripts/run-ayn-thor-avd.sh
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

Set `AYN_THOR_VSYNC_RATE=120` when validating the upper display at its target refresh rate.

## Releases

Use `vMAJOR.MINOR.PATCH-alpha.N`, `vMAJOR.MINOR.PATCH-beta.N`, or `vMAJOR.MINOR.PATCH` tags for prereleases and stable releases.
