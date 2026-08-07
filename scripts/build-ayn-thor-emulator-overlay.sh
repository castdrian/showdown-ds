#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="${AEMU_SOURCE_ROOT:?Set AEMU_SOURCE_ROOT to a synced Android Emulator source checkout}"
qemu_root="$source_root/external/qemu"
patch_file="$repo_root/tools/android-emulator/ayn-thor-single-window.patch"
build_root="$source_root/objs-showdown-ds"

if [[ ! -d "$qemu_root/.git" ]]; then
    printf '%s\n' "The Android Emulator source checkout was not found at $qemu_root"
    exit 1
fi

if ! git -C "$qemu_root" apply --check "$patch_file"; then
    printf '%s\n' "The AYN Thor patch does not apply to this emulator source revision."
    exit 1
fi

git -C "$qemu_root" apply "$patch_file"
"$qemu_root/android/rebuild.sh" --out "$build_root"

case "$(uname -s)-$(uname -m)" in
    Darwin-arm64|Darwin-aarch64)
        qemu_name="qemu-system-aarch64"
        ;;
    Darwin-x86_64|Linux-x86_64|Linux-amd64)
        qemu_name="qemu-system-x86_64"
        ;;
    MINGW64_NT-*-x86_64|MSYS_NT-*-x86_64)
        qemu_name="qemu-system-x86_64.exe"
        ;;
    *)
        printf '%s\n' "Unsupported host: $(uname -s)-$(uname -m)"
        exit 1
        ;;
esac

qemu_binary="$(find "$build_root" -type f -name "$qemu_name" -print -quit)"
if [[ -z "$qemu_binary" ]]; then
    printf '%s\n' "The built QEMU binary was not found under $build_root"
    exit 1
fi

"$repo_root/scripts/install-ayn-thor-emulator-overlay.sh" "$qemu_binary"
