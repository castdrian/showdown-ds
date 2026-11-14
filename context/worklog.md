# Worklog

Newest first.

## Step 1: scaffolding

- Branch `feature/webview-core` from the experiment branch (contains the terrain fix, the host cooldown and the WebView-only experiment).
- Added `local` build type (`dev.adrian.showdown.local`, `-local`, `USE_LOCAL_SERVER=true`) and `ShowdownServerEndpoint.defaultFor`, with `ShowdownDefaultEndpointTest`.
- Added `scripts/local-showdown.sh` (pinned upstream server from the CI workflow, guest logins) and `tools/local-showdown/bot.ts` (TypeScript, Node 22 type stripping, built-in WebSocket; modes `search`, `challenge`, `accept`, optional `--stall-after-turn`). Verified two bots finish a battle against each other.
- Added `build.sh` (`--variant`, `--test`), `emulate.sh` (reuses a running emulator, builds, sideloads, launches) and `AGENTS.md`.
- Verified: `./emulate.sh` installs `.local` next to the untouched `.debug` install; the `.local` app starts in a clean lobby.
