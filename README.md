# Showdown!

https://github.com/user-attachments/assets/f99aa366-a1d2-4abf-b098-6518847faf44

Native dual-screen Pokémon Showdown! client for Android, built in Kotlin with a Vulkan foundation and tailored for the AYN Thor. It uses Showdown’s battle assets, animated XY sprites, move effects, audio, live protocol, custom-server support, touch, and controller input.

## Run

```sh
./scripts/setup-android.sh
./scripts/create-ayn-thor-avd.sh
./scripts/run-ayn-thor-avd.sh
gradle assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

The repo-local AVD uses the Thor’s 1920 × 1080 upper display and 1240 × 1080 lower display. Use `AYN_THOR_VSYNC_RATE=120` to validate at the hardware refresh rate.

## Releases

Tag `vMAJOR.MINOR.PATCH-alpha.N` or `vMAJOR.MINOR.PATCH-beta.N` for prereleases, or `vMAJOR.MINOR.PATCH` for stable releases. The workflow attaches an Obtainium-compatible APK to GitHub Releases.
