#!/usr/bin/env bash
# Starts the AYN Thor emulator (if it is not already running), builds the app and sideloads it.
#
# Usage: ./emulate.sh [--variant local|debug] [--no-build] [--restart-emulator] [--login NAME]
#
# The default "local" variant has its own app id and data, talks to the local development
# server, and never touches a saved Showdown login. "--variant debug" installs over the normal
# debug app and reuses whatever login that install holds.
#
# "--login NAME" (local variant only) signs the app in as NAME on the local server, which needs no
# password. A named user keeps its unfinished battles across reconnects, unlike a guest.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
source "$repo_root/scripts/android-sdk-root.sh"
sdk_root="$(android_sdk_root)"
adb_binary="$sdk_root/platform-tools/adb"
state_dir="${XDG_STATE_HOME:-$HOME/.local/state}/showdown-ds"
variant="local"
build_app=1
restart_emulator=0
login_name=""
boot_attempts=300
server_port="${SHOWDOWN_SERVER_PORT:-8000}"
main_activity="dev.adrian.showdown.MainActivity"

usage() {
    printf '%s\n' "Usage: ./emulate.sh [--variant local|debug] [--no-build] [--restart-emulator] [--login NAME]"
}

parse_arguments() {
    while (( $# > 0 )); do
        case "$1" in
            --variant)
                variant="${2:?--variant needs a value}"
                shift 2
                ;;
            --no-build)
                build_app=0
                shift
                ;;
            --restart-emulator)
                restart_emulator=1
                shift
                ;;
            --login)
                login_name="${2:?--login needs a name}"
                shift 2
                ;;
            -h|--help)
                usage
                exit 0
                ;;
            *)
                usage
                exit 1
                ;;
        esac
    done
    if [[ "$variant" != "local" && "$variant" != "debug" ]]; then
        usage
        exit 1
    fi
    if [[ -n "$login_name" && "$variant" != "local" ]]; then
        printf '%s\n' "--login is only available with the local variant."
        exit 1
    fi
}

application_id() {
    printf '%s\n' "dev.adrian.showdown.$variant"
}

apk_path() {
    printf '%s\n' "$repo_root/app/build/outputs/apk/$variant/app-$variant.apk"
}

emulator_is_running() {
    "$adb_binary" devices 2>/dev/null | grep -Eq '^emulator-[0-9]+[[:space:]]+device'
}

emulator_has_booted() {
    [[ "$("$adb_binary" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]
}

start_emulator() {
    mkdir -p "$state_dir"
    local emulator_log="$state_dir/emulator.log"
    printf '%s\n' "Starting the AYN Thor emulator (log: $emulator_log)"
    AYN_THOR_ALLOW_STOCK_EMULATOR="${AYN_THOR_ALLOW_STOCK_EMULATOR:-1}" \
        nohup "$repo_root/scripts/run-ayn-thor-avd.sh" >"$emulator_log" 2>&1 &
}

wait_for_boot() {
    local attempt=0
    while (( attempt < boot_attempts )); do
        (( attempt += 1 ))
        if emulator_is_running && emulator_has_booted; then
            return 0
        fi
        sleep 1
    done
    printf '%s\n' "The emulator did not finish booting; see $state_dir/emulator.log"
    exit 1
}

ensure_emulator() {
    if (( restart_emulator == 1 )) || ! emulator_is_running; then
        start_emulator
    fi
    wait_for_boot
}

local_server_is_listening() {
    (exec 3<>"/dev/tcp/127.0.0.1/$server_port") 2>/dev/null
}

ensure_local_server() {
    if [[ "$variant" != "local" ]] || local_server_is_listening; then
        return
    fi
    mkdir -p "$state_dir"
    printf '%s\n' "Starting the local Showdown server (log: $state_dir/local-server.log)"
    nohup "$repo_root/scripts/local-showdown.sh" >"$state_dir/local-server.log" 2>&1 &
}

build_variant() {
    if (( build_app == 1 )); then
        "$repo_root/build.sh" --variant "$variant"
    fi
}

launch_extras() {
    if [[ -n "$login_name" ]]; then
        printf '%s\n' "--es" "dev_login_username" "$login_name"
    fi
}

install_and_launch() {
    local -a extras
    mapfile -t extras < <(launch_extras)
    "$adb_binary" install -r "$(apk_path)"
    if [[ -n "$login_name" ]]; then
        "$adb_binary" shell am force-stop "$(application_id)"
    fi
    "$adb_binary" shell am start -n "$(application_id)/$main_activity" "${extras[@]}"
}

parse_arguments "$@"
if [[ ! -x "$adb_binary" ]]; then
    printf '%s\n' "adb was not found under $sdk_root. Run ./scripts/setup-android.sh first."
    exit 1
fi
ensure_local_server
build_variant
ensure_emulator
install_and_launch
