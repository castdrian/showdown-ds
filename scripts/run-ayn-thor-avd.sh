#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_root/scripts/android-sdk-root.sh"
sdk_root="$(android_sdk_root)"
avd_home="$repo_root/.android/avd"
emulator="$sdk_root/emulator/emulator"
avd_name="AYN_Thor_API_34"
overlay_emulator="$repo_root/.emulator-overlay/emulator"
audio_args=()
vsync_rate="${AYN_THOR_VSYNC_RATE:-120}"
gpu_mode="${AYN_THOR_GPU_MODE:-auto}"
boot_animation_args=()
multidisplay_args=(-feature MultiDisplay -multidisplay "1,1240,1080,420,1347")

case "$vsync_rate" in
    60|90|120) ;;
    *)
        printf '%s\n' "AYN_THOR_VSYNC_RATE must be 60, 90, or 120."
        exit 1
        ;;
esac

case "$gpu_mode" in
    auto|host|software|swiftshader|swangle) ;;
    *)
        printf '%s\n' "AYN_THOR_GPU_MODE must be auto, host, software, swiftshader, or swangle."
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
adb_binary="$sdk_root/platform-tools/adb"
device_serial="${ANDROID_SERIAL:-emulator-5554}"

wait_for_android_boot() {
    local attempt=0
    local boot_completed
    local device_state
    while (( attempt < 120 )); do
        (( attempt += 1 ))
        device_state="$($adb_binary -s "$device_serial" get-state 2>/dev/null || true)"
        if [[ "$device_state" == "device" ]]; then
            boot_completed="$($adb_binary -s "$device_serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')"
            if [[ "$boot_completed" == "1" ]]; then
                return 0
            fi
        fi
        if ! kill -0 "$emulator_pid" 2>/dev/null; then
            return 1
        fi
        sleep 1
    done
    return 1
}

activate_secondary_display() {
    local attempt=0
    local display_info
    while (( attempt < 20 )); do
        (( attempt += 1 ))
        if "$adb_binary" -s "$device_serial" shell am broadcast \
            -a com.android.emulator.multidisplay.START \
            -n com.android.emulator.multidisplay/.MultiDisplayServiceReceiver \
            --user 0 >/dev/null 2>&1; then
            display_info="$($adb_binary -s "$device_serial" shell dumpsys display 2>/dev/null || true)"
            if [[ "$display_info" == *"virtual:com.android.emulator.multidisplay"* ]]; then
                return 0
            fi
        fi
        sleep 1
    done
    return 1
}

stop_emulator() {
    if kill -0 "$emulator_pid" 2>/dev/null; then
        kill "$emulator_pid" 2>/dev/null || true
    fi
}

"$emulator" \
    -avd "$avd_name" \
    -gpu "$gpu_mode" \
    -vsync-rate "$vsync_rate" \
    "${boot_animation_args[@]}" \
    "${audio_args[@]}" \
    "${multidisplay_args[@]}" \
    "$@" &
emulator_pid=$!
trap stop_emulator EXIT INT TERM

if ! wait_for_android_boot; then
    printf '%s\n' "The AYN Thor emulator exited before Android finished booting."
    exit 1
fi

if ! activate_secondary_display; then
    printf '%s\n' "The AYN Thor secondary display did not become available."
    exit 1
fi

wait "$emulator_pid"
