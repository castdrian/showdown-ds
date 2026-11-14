# Worklog

Newest first.

## Step 3: WebView-core top screen

- 3a: the Showdown client WebView is the permanent battle scene (opaque, full chrome). Its page moved from an 780-line Kotlin string to `assets/showdown-battle-scene.html` rendered by `ShowdownBattleSceneDocument` (placeholders for playback speed bounds, perspective guard, debug flag); chrome-hiding JS removed. Lightweight (no-WebView) playback and `BattlePlaybackPolicy` removed; a missing WebView reports a clear status.
- 3b: `BattleSceneView` (3.3k lines of native battle drawing, inspect sheet, feed, team preview, touch handling) replaced by `LobbyView` (lobby card + replay-loading screen + accessibility description). Deleted nine files used only by it (`BattleCardContent`, `BattleCardLayout`, `BattleClockPresentation`, `BattleCombatantLayout`, `BattleFieldVisualComposer`, `BattleMoveVisualStyleResolver`, `BattleTeamPreviewLayout`, `ShowdownBattleLayout`, `ShowdownBattleMovePresentation`) and their tests.
- Tests removed because the feature they covered is gone: `BattlePlaybackPolicyTest`, `BattleSceneEffectsContractTest`, the two low-memory playback tests, chrome-hiding and `MutationObserver` assertions, native status-card assertions in `ShowdownReplayPlaybackParityTest`, native scene lifecycle assertions in `MainActivityLifecycleContractTest`/`ShowdownSpriteCacheContractTest`; instrumented `MainActivityReadmeCaptureTest`, `MainActivityReplayPlaybackParityTest` and two native-scene tests in `ShowdownBattleLogRendererParityTest`. Contract tests that grep the page now read the Kotlin view plus the asset (assertions unchanged).
- Follow-ups: README screenshots show the old native scene and need regenerating; `BattleAudio` still has a low-memory mode that nothing enables; `ShowdownSpriteCache.requestTeamPreviewPokemon` and related fallbacks are unused; `BattlePlaybackTiming.pauseAfter` still takes a feed budget that is always 0.

## Step 2: reopening battle, Resume and Forfeit

**Root cause.** Three paths auto-opened any battle the account was in:
1. `updatesearch` `games` -> `ShowdownLobbyState.battleForReconnect` -> `joinMatchedBattle` joined the first reported game.
2. Any server-pushed `|init|battle` frame was applied as the active battle.
3. `active_battle_room` (saved state + `showdown_live` prefs) was restored on every launch.
Showdown resends every unfinished battle of a named account on each login, so an account with a never-finishing battle reopened it forever. Captured on the local server (named user, stalled bot, relog): `|updateuser| Name|1|`, then `|updatesearch|{"games":{room:format}}`, then the room's `|init|battle` frames.

**Fix.**
- `ShowdownUnfinishedBattles`: after a named login the rooms in the first `updatesearch` that the app did not expect (not the active room, not a pending join) are held and their packets buffered. Resume replays the buffer through `applyBattleRoomPacket`; a held battle that ends or disappears is dropped. Battles that start later (match, accepted challenge) open as before.
- `battleForReconnect` skips held rooms; debuggable builds no longer restore `active_battle_room` (`ShowdownBattleRecovery.restoredActiveRoomId`).
- UI: prompt dialog (Resume / Forfeit with confirm / Later) once every held battle has its title, plus a "Resume battle" menu entry (menu item 3 replaces "Challenge player" while something is held). Forfeit sends `/forfeit` to the held room.
- Refactor: battle-room packet handling extracted from the connection listener into `applyBattleRoomPacket`; `joinMatchedBattle` split into the join command and `beginBattleParticipation`.
- Dev tooling: `ShowdownDevLogin` (local build only) signs in with `/trn Name,0,` from the `dev_login_username` launch extra (`./emulate.sh --login NAME`); local server config adds `noipchecks`/`nothrottle`; bot `search` mode sends `/utm null` first and logs server errors.

**Verified end to end** on the emulator (`.local` build, local server, stalled bot): relaunch shows the prompt instead of reopening; Resume opens the battle; Forfeit ends it (bot saw `|win|`) and the lobby no longer offers Resume.

Tests: `ShowdownUnfinishedBattlesTest`, `ShowdownLobbyReconnectExclusionTest`, `BattleSessionResumeMenuTest`, `ShowdownUnfinishedBattlePresentationTest`, `ShowdownBattleRestorePolicyTest`, `ShowdownDevLoginTest`.

## Step 1: scaffolding

- Branch `feature/webview-core` from the experiment branch (contains the terrain fix, the host cooldown and the WebView-only experiment).
- Added `local` build type (`dev.adrian.showdown.local`, `-local`, `USE_LOCAL_SERVER=true`) and `ShowdownServerEndpoint.defaultFor`, with `ShowdownDefaultEndpointTest`.
- Added `scripts/local-showdown.sh` (pinned upstream server from the CI workflow, guest logins) and `tools/local-showdown/bot.ts` (TypeScript, Node 22 type stripping, built-in WebSocket; modes `search`, `challenge`, `accept`, optional `--stall-after-turn`). Verified two bots finish a battle against each other.
- Added `build.sh` (`--variant`, `--test`), `emulate.sh` (reuses a running emulator, builds, sideloads, launches) and `AGENTS.md`.
- Verified: `./emulate.sh` installs `.local` next to the untouched `.debug` install; the `.local` app starts in a clean lobby.
