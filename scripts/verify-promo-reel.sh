#!/usr/bin/env bash

set -euo pipefail

if [[ $# -ne 1 ]]; then
    printf '%s\n' "Usage: $0 /path/to/promo.mp4"
    exit 64
fi

input="$1"

if [[ ! -f "$input" ]]; then
    printf '%s\n' "Promo reel not found: $input"
    exit 66
fi

video_codec="$(ffprobe -v error -select_streams v:0 -show_entries stream=codec_name -of csv=p=0 "$input")"
video_width="$(ffprobe -v error -select_streams v:0 -show_entries stream=width -of csv=p=0 "$input")"
video_height="$(ffprobe -v error -select_streams v:0 -show_entries stream=height -of csv=p=0 "$input")"
video_rate="$(ffprobe -v error -select_streams v:0 -show_entries stream=avg_frame_rate -of csv=p=0 "$input")"
video_frames="$(ffprobe -v error -select_streams v:0 -show_entries stream=nb_frames -of csv=p=0 "$input")"
duration="$(ffprobe -v error -show_entries format=duration -of csv=p=0 "$input")"
audio_codec="$(ffprobe -v error -select_streams a:0 -show_entries stream=codec_name -of csv=p=0 "$input")"
audio_duration="$(ffprobe -v error -select_streams a:0 -show_entries stream=duration -of csv=p=0 "$input")"

if [[ "$video_codec" != "h264" || "$video_width" != "1920" || "$video_height" != "2160" || "$video_rate" != "30/1" || "$audio_codec" != "aac" ]]; then
    printf '%s\n' "Promo reel format check failed."
    printf 'video=%s %sx%s %s fps frames=%s audio=%s\n' "$video_codec" "$video_width" "$video_height" "$video_rate" "$video_frames" "${audio_codec:-missing}"
    exit 1
fi

if ! awk -v value="$duration" 'BEGIN { exit !(value >= 29.5 && value <= 30.5) }'; then
    printf '%s\n' "Promo reel duration must be approximately 30 seconds: $duration"
    exit 1
fi

if ! awk -v value="$audio_duration" -v video="$duration" 'BEGIN { difference = value - video; if (difference < 0) difference = -difference; exit !(difference <= 0.15) }'; then
    printf '%s\n' "Promo reel audio and video durations differ: $audio_duration vs $duration"
    exit 1
fi

printf 'Promo reel valid: %sx%s, %s fps, %s frames, %ss, AAC audio %ss\n' "$video_width" "$video_height" "$video_rate" "$video_frames" "$duration" "$audio_duration"
