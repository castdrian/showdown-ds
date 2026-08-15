#!/usr/bin/env bash

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source_root="${AEMU_SOURCE_ROOT:?Set AEMU_SOURCE_ROOT to a synced Android Emulator source checkout}"
qemu_root="$source_root/external/qemu"
patch_file="$repo_root/tools/android-emulator/ayn-thor-single-window.patch"
build_root="$source_root/objs-showdown-ds"
ccache_mode="${AEMU_THOR_CCACHE:-auto}"

if [[ "$ccache_mode" == "auto" ]]; then
    case "$(uname -s)" in
        Darwin)
            sccache_host="darwin"
            ;;
        Linux)
            sccache_host="linux"
            ;;
        MINGW*|MSYS*)
            sccache_host="windows"
            ;;
        *)
            sccache_host=""
            ;;
    esac
    if [[ -n "$sccache_host" && ! -x "$source_root/prebuilts/android-emulator-build/common/sccache/${sccache_host}-x86_64/sccache" ]]; then
        ccache_mode="none"
    fi
fi

if [[ ! -d "$qemu_root/.git" ]]; then
    printf '%s\n' "The Android Emulator source checkout was not found at $qemu_root"
    exit 1
fi

if git -C "$qemu_root" apply --check "$patch_file"; then
    git -C "$qemu_root" apply "$patch_file"
elif ! git -C "$qemu_root" apply --reverse --check "$patch_file"; then
    printf '%s\n' "The AYN Thor patch does not apply to this emulator source revision."
    exit 1
fi
"$qemu_root/android/rebuild.sh" --ccache "$ccache_mode" --out "$build_root"

case "$(uname -s)-$(uname -m)" in
    Darwin-arm64|Darwin-aarch64)
        qemu_name="qemu-system-aarch64"
        qemu_headless_name="qemu-system-aarch64-headless"
        ;;
    Darwin-x86_64|Linux-x86_64|Linux-amd64)
        qemu_name="qemu-system-x86_64"
        qemu_headless_name="qemu-system-x86_64-headless"
        ;;
    MINGW64_NT-*-x86_64|MSYS_NT-*-x86_64)
        qemu_name="qemu-system-x86_64.exe"
        qemu_headless_name="qemu-system-x86_64-headless.exe"
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

case "$(uname -s)-$(uname -m)" in
    Darwin-arm64|Darwin-aarch64)
        ninja_host="darwin-x86"
        ;;
    Darwin-x86_64)
        ninja_host="darwin-x86"
        ;;
    Linux-x86_64|Linux-amd64)
        ninja_host="linux-x86"
        ;;
    *)
        ninja_host=""
        ;;
esac

if [[ -n "$ninja_host" && -f "$build_root/build.ninja" ]]; then
    ninja_binary="$source_root/prebuilts/ninja/$ninja_host/ninja"
    if [[ ! -x "$ninja_binary" ]]; then
        printf '%s\n' "The Ninja binary was not found at $ninja_binary"
        exit 1
    fi
    "$ninja_binary" -C "$build_root" "$qemu_name" "$qemu_headless_name" gfxstream_backend
fi

qemu_headless_binary="$(find "$build_root" -type f -name "$qemu_headless_name" -print -quit)"
if [[ -z "$qemu_headless_binary" ]]; then
    printf '%s\n' "The built headless QEMU binary was not found under $build_root"
    exit 1
fi

"$repo_root/scripts/install-ayn-thor-emulator-overlay.sh" "$qemu_binary" "$qemu_headless_binary" "$build_root"
