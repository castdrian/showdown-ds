#!/usr/bin/env bash

set -euo pipefail

if [[ $# -lt 2 || $# -gt 3 ]]; then
    printf '%s\n' "Usage: $0 /path/to/capture.mp4 /path/to/output.mp4 [/path/to/audio]"
    exit 64
fi

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
input="$1"
output="$2"
audio="${3:-}"
output_directory="$(dirname "$output")"
temporary_directory="$(mktemp -d "${TMPDIR:-/tmp}/showdown-promo.XXXXXX")"
temporary_output="$temporary_directory/promo.mp4"

cleanup() {
    rm -rf "$temporary_directory"
}

trap cleanup EXIT

mkdir -p "$output_directory"

if [[ -n "$audio" ]]; then
    ffmpeg -hide_banner -loglevel error -y \
        -i "$input" \
        -i "$audio" \
        -map 0:v:0 \
        -map 1:a:0 \
        -vf "fps=30:round=near,setpts=PTS-STARTPTS" \
        -fps_mode cfr \
        -c:v libx264 \
        -preset medium \
        -crf 18 \
        -pix_fmt yuv420p \
        -c:a aac \
        -b:a 192k \
        -ar 44100 \
        -ac 2 \
        -af "aresample=async=1:first_pts=0" \
        -shortest \
        -movflags +faststart \
        "$temporary_output"
else
    ffmpeg -hide_banner -loglevel error -y \
        -i "$input" \
        -map 0:v:0 \
        -map 0:a:0? \
        -vf "fps=30:round=near,setpts=PTS-STARTPTS" \
        -fps_mode cfr \
        -c:v libx264 \
        -preset medium \
        -crf 18 \
        -pix_fmt yuv420p \
        -c:a aac \
        -b:a 192k \
        -ar 44100 \
        -ac 2 \
        -af "aresample=async=1:first_pts=0" \
        -shortest \
        -movflags +faststart \
        "$temporary_output"
fi

"$repo_root/scripts/verify-promo-reel.sh" "$temporary_output"
mv "$temporary_output" "$output"
