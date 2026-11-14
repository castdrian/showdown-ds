#!/usr/bin/env bash
# Builds the app, optionally linting and running the test suites afterwards.
#
# Usage: ./build.sh [--variant debug|local|release] [--test]

set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
variant="debug"
run_tests=0

usage() {
    printf '%s\n' "Usage: ./build.sh [--variant debug|local|release] [--test]"
}

parse_arguments() {
    while (( $# > 0 )); do
        case "$1" in
            --variant)
                variant="${2:?--variant needs a value}"
                shift 2
                ;;
            --test)
                run_tests=1
                shift
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
}

gradle_task_for_variant() {
    case "$variant" in
        debug) printf '%s\n' ":app:assembleDebug" ;;
        local) printf '%s\n' ":app:assembleLocal" ;;
        release) printf '%s\n' ":app:assembleRelease" ;;
        *)
            usage
            exit 1
            ;;
    esac
}

run_go_tests() {
    if ! command -v go >/dev/null 2>&1; then
        printf '%s\n' "go is not installed; skipping the Go tooling tests"
        return
    fi
    (cd "$repo_root" && go test scripts/*.go tools/showdown-battle-data/*.go tools/compose-thor-screenshot/*.go)
}

parse_arguments "$@"
cd "$repo_root"
./gradlew --console=plain "$(gradle_task_for_variant)"

if (( run_tests == 1 )); then
    ./gradlew --console=plain :app:lintDebug :app:testDebugUnitTest
    run_go_tests
fi
