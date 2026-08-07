#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_root/scripts/android-sdk-root.sh"
sdk_root="$(android_sdk_root)"
avd_home="$repo_root/.android/avd"
emulator="$sdk_root/emulator/emulator"
avd_name="AYN_Thor_API_34_Vulkan"
overlay_emulator="$repo_root/.emulator-overlay/emulator"

if [[ ! -x "$emulator" ]]; then
    printf '%s\n' "The Android emulator was not found at $emulator. Run ./scripts/setup-android.sh first."
    exit 1
fi

if [[ -x "$overlay_emulator" ]]; then
    emulator="$overlay_emulator"
fi

if [[ ! -f "$avd_home/$avd_name.ini" ]]; then
    "$repo_root/scripts/create-ayn-thor-avd.sh"
fi

export ANDROID_AVD_HOME="$avd_home"
export ANDROID_SDK_ROOT="$sdk_root"
exec "$emulator" \
    -avd "$avd_name" \
    -gpu host \
    -vsync-rate 120 \
    "$@"
