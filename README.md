# Showdown!

Native Android Pokémon Showdown client for the AYN Thor dual-screen handheld.

https://github.com/user-attachments/assets/ec825ee2-5ebe-4141-9d56-aecd09af57c8

## Hardware target

- Upper display: 1920 × 1080 pixels, 15.24 cm, 120 Hz.
- Lower display: 1240 × 1080 pixels, 9.96 cm, 60 Hz.
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

## Promo capture

Normalize a fresh Thor capture to the stacked 1920 × 2160, 30 fps format before uploading it to GitHub:

```sh
./scripts/normalize-promo-reel.sh capture.mp4 media/showdown-promo.mp4
./scripts/verify-promo-reel.sh media/showdown-promo.mp4
```

## Support the project

If this client is useful to you, [sponsor the project on GitHub](https://github.com/sponsors/castdrian) or [support it on Ko-fi](https://ko-fi.com/castdrian).

## Releases

Use `vMAJOR.MINOR.PATCH-alpha.N`, `vMAJOR.MINOR.PATCH-beta.N`, or `vMAJOR.MINOR.PATCH` tags for prereleases and stable releases.
