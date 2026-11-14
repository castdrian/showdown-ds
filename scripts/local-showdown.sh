#!/usr/bin/env bash
# Runs a local Pokémon Showdown server for development and testing the app without a real account.
# The server is the same pinned commit CI tests against, with guest logins allowed.

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
workflow="$repo_root/.github/workflows/android-ci.yml"
server_dir="${SHOWDOWN_SERVER_DIR:-${XDG_CACHE_HOME:-$HOME/.cache}/showdown-ds/pokemon-showdown}"
server_repository="https://github.com/smogon/pokemon-showdown.git"
server_port="${SHOWDOWN_SERVER_PORT:-8000}"
server_config="$repo_root/tools/showdown-upstream-ci/config.js"

pinned_server_ref() {
    sed -n 's/^[[:space:]]*ref:[[:space:]]*\([0-9a-f]\{40\}\)[[:space:]]*$/\1/p' "$workflow" | head -n 1
}

require_command() {
    if ! command -v "$1" >/dev/null 2>&1; then
        printf '%s\n' "Missing required command: $1"
        exit 1
    fi
}

fetch_server() {
    local ref="$1"
    if [[ -d "$server_dir/.git" ]] && [[ "$(git -C "$server_dir" rev-parse HEAD)" == "$ref" ]]; then
        return
    fi
    mkdir -p "$server_dir"
    git -C "$server_dir" init --quiet
    git -C "$server_dir" fetch --quiet --depth 1 "$server_repository" "$ref"
    git -C "$server_dir" checkout --quiet --force FETCH_HEAD
}

install_server_dependencies() {
    if [[ ! -d "$server_dir/node_modules" ]]; then
        (cd "$server_dir" && npm ci --no-audit --no-fund)
    fi
}

require_command git
require_command node
require_command npm

server_ref="$(pinned_server_ref)"
if [[ -z "$server_ref" ]]; then
    printf '%s\n' "Could not read the pinned upstream server ref from $workflow"
    exit 1
fi

fetch_server "$server_ref"
install_server_dependencies
cp "$server_config" "$server_dir/config/config.js"
printf '%s\n' "Starting local Showdown server on port $server_port (guest logins allowed)"
cd "$server_dir"
exec node pokemon-showdown "$server_port"
