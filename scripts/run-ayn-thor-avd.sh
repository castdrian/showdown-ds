#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_root/scripts/android-sdk-root.sh"
sdk_root="$(android_sdk_root)"
avd_home="$repo_root/.android/avd"
emulator="$sdk_root/emulator/emulator"
avd_name="AYN_Thor_API_34_Vulkan"
overlay_emulator="$repo_root/.emulator-overlay/emulator"
audio_args=()
vsync_rate="${AYN_THOR_VSYNC_RATE:-60}"
boot_animation_args=()

case "$vsync_rate" in
    60|90|120) ;;
    *)
        printf '%s\n' "AYN_THOR_VSYNC_RATE must be 60, 90, or 120."
        exit 1
        ;;
esac

if [[ "$(uname -s)" == "Darwin" && -z "${AYN_THOR_AUDIO_BACKEND:-}" ]]; then
    audio_args=(-audio coreaudio)
elif [[ -n "${AYN_THOR_AUDIO_BACKEND:-}" ]]; then
    audio_args=(-audio "$AYN_THOR_AUDIO_BACKEND")
fi

if [[ "${AYN_THOR_BOOT_ANIMATION:-0}" != "1" ]]; then
    boot_animation_args=(-no-boot-anim)
fi

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
    -vsync-rate "$vsync_rate" \
    "${boot_animation_args[@]}" \
    "${audio_args[@]}" \
    "$@"
