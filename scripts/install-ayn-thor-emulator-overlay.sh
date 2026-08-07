#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$repo_root/scripts/android-sdk-root.sh"
sdk_root="$(android_sdk_root)"
sdk_emulator_root="$sdk_root/emulator"
overlay_root="$repo_root/.emulator-overlay"
qemu_binary="${1:?Usage: ./scripts/install-ayn-thor-emulator-overlay.sh /path/to/qemu-system-binary}"

case "$(uname -s)-$(uname -m)" in
    Darwin-arm64|Darwin-aarch64)
        host_directory="darwin-aarch64"
        qemu_name="qemu-system-aarch64"
        ;;
    Darwin-x86_64)
        host_directory="darwin-x86_64"
        qemu_name="qemu-system-x86_64"
        ;;
    Linux-x86_64|Linux-amd64)
        host_directory="linux-x86_64"
        qemu_name="qemu-system-x86_64"
        ;;
    MINGW64_NT-*-x86_64|MSYS_NT-*-x86_64)
        host_directory="windows-x86_64"
        qemu_name="qemu-system-x86_64.exe"
        ;;
    *)
        printf '%s\n' "Unsupported host: $(uname -s)-$(uname -m)"
        exit 1
        ;;
esac

if [[ ! -x "$sdk_emulator_root/emulator" ]]; then
    printf '%s\n' "The Android emulator was not found at $sdk_emulator_root. Run ./scripts/setup-android.sh first."
    exit 1
fi

if [[ ! -f "$qemu_binary" ]]; then
    printf '%s\n' "The patched QEMU binary was not found: $qemu_binary"
    exit 1
fi

if [[ -e "$overlay_root" ]]; then
    printf '%s\n' "The overlay already exists at $overlay_root. Move it aside before installing another build."
    exit 1
fi

mkdir -p "$overlay_root/qemu/$host_directory"
for sdk_entry in "$sdk_emulator_root"/*; do
    entry_name="$(basename "$sdk_entry")"
    if [[ "$entry_name" != "emulator" && "$entry_name" != "qemu" ]]; then
        ln -s "$sdk_entry" "$overlay_root/$entry_name"
    fi
done
cp "$sdk_emulator_root/emulator" "$overlay_root/emulator"
cp "$qemu_binary" "$overlay_root/qemu/$host_directory/$qemu_name"
chmod +x "$overlay_root/emulator"
chmod +x "$overlay_root/qemu/$host_directory/$qemu_name"

printf '%s\n' "Installed the AYN Thor emulator overlay in $overlay_root"
