# Environment (this dev machine)

- WSL2 Ubuntu, x86_64, KVM available to the user (kvm group).
- JDK 17 at `~/toolchain/jdk`, Android SDK at `~/Android/Sdk`, helpers in `~/toolchain/bin` (ripgrep). `source ~/toolchain/env.sh` sets `JAVA_HOME`, `ANDROID_HOME`, `PATH` and `LD_LIBRARY_PATH`.
- No sudo, so `libpulse0` (needed by the emulator's qemu) and its dependencies were unpacked with `apt download` + `dpkg -x` into `~/toolchain/syslibs`; `env.sh` adds them to `LD_LIBRARY_PATH`. `sudo apt install libpulse0` makes this unnecessary.
- Emulator: stock Android emulator with the `AYN_Thor_API_34` AVD (1920x1080 + 1240x1080). The patched Thor overlay is not built, so screen order is not guaranteed.
- `adb screencap` only captures the upper (primary) display.
- Local Showdown server: `scripts/local-showdown.sh`, cached under `~/.cache/showdown-ds/pokemon-showdown`, port 8000. Logs from `emulate.sh` go to `~/.local/state/showdown-ds/`.
