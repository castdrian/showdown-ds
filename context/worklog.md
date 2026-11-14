# Worklog

Newest first.

## Follow-up: Resume fast-forwards instead of replaying

- Reported on the developer's real battle: after Resume the battle "restarted" and no sprites showed. Cause: Resume fed the whole buffered history through normal paced playback, replaying the battle from turn 1 with every animation. Resume now applies the history to the session at once (`applyBattlePacketsInstantly`) and seeds the web scene silently from the full protocol history with a non-zero barrier token (`fastForwardBattle`, same recovery call as the background-resume path). The scene reaches the current state in about 10 s on the slow emulator (the first frames still show the start of the battle while the web client steps through the history).
- The first `.debug` reinstall also replayed the battle without a prompt: state left by the older build (that install predates the held-battle logic). A fresh relaunch with temporary logging showed the real server's order matches the recording and the battle is held.
- Verified locally with a named user (`--login ResumeUser1`), a bot challenge accepted in the app, two played turns, relaunch and Resume. No automated test: the wiring lives in `MainActivity`.

## Step 5: cleanup, lint, docs

- Dead code after the native scene went: `BattleSceneTiming` (its summon duration moved into `BattleSession`, the animation pause into `BattlePlaybackTiming`), the always-zero `feedPlaybackBudgetMillis` parameter of `BattlePlaybackTiming.pauseAfter`, `ShowdownSpriteCache.requestItem/requestLadderBadge/requestBackdrop`, the cache's decoded `fallbackBackdrop` bitmap and its drawable. Removed their tests (`BattleSceneTimingTest`, the feed-budget test, the backdrop contract test); the replay parity tests no longer pass the budget and still pass.
- Android Lint (`:app:lintDebug`) passes with no errors; `build.sh --test` and CI now run it. ktlint was not added (needs approval).
- README rewritten for the new architecture and the `emulate.sh` / local server workflow; `AGENTS.md`, `Dockerfile` (+ `.dockerignore`) added; in-app changelog gained entries for the WebView scene, Resume/Forfeit and the richer team cards.
- Full instrumented suite on the emulator (`connectedLocalAndroidTest`, 33 tests) found one problem: the persisted dev login sent `/trn LocalUser,0,` ahead of `MainActivityLoginFlowTest`'s real login. The dev login now yields whenever credentials or a saved session exist (`ShowdownDevLogin.signInUsername`); all 33 tests pass. `docker run --rm showdown-ds` (`./build.sh --test`) also passes.
- Left for later (all unused by the app now): `BattleSpriteRequestTracker`/`BattleSpriteRequests.forScene` (tests only), `BattleAudio` low-memory mode and `playCuePreview`/`diagnosticSnapshot`, README screenshots of the old native scene.

## Step 4: Pokémon-tab cards

- Found and fixed a data bug: species types and item display names were only loaded by `ShowdownMoveDex.load()` (the team editor) and `loadTeamCoverageData()`, never by the battle path (`loadBattleDetails`), so in a battle the Pokémon tab showed no type chips and raw item ids. `loadBattleDetails` now also parses species types and item names from the files it already fetches (`ShowdownMoveDex.parseBattleDetailTables`, `applyBattleDetailTables`).
- Cards on the lower-screen Pokémon tab (both teams) now show the held item next to level and gender, the active Pokémon's stat boosts, and a major status condition in the state chip (`TeamCardDetailsPresenter`; `BattleSession.activeBoostsFor`). Unknown items are omitted.
- Verified on the emulator (lower display captured with `adb emu screenrecord screenshot --display 1`): type chips, proper item names and HP for your team and the opponent's revealed Pokémon. Boosts and status text are covered by unit tests only.
- Tests: `ShowdownBattleDetailTablesTest`, `TeamCardDetailsPresenterTest`, `BattleSessionActiveBoostsTest`.

## Step 3: WebView-core top screen

- 3a: the Showdown client WebView is the permanent battle scene (opaque, full chrome). Its page moved from an 780-line Kotlin string to `assets/showdown-battle-scene.html` rendered by `ShowdownBattleSceneDocument` (placeholders for playback speed bounds, perspective guard, debug flag); chrome-hiding JS removed. Lightweight (no-WebView) playback and `BattlePlaybackPolicy` removed; a missing WebView reports a clear status.
- 3b: `BattleSceneView` (3.3k lines of native battle drawing, inspect sheet, feed, team preview, touch handling) replaced by `LobbyView` (lobby card + replay-loading screen + accessibility description). Deleted nine files used only by it (`BattleCardContent`, `BattleCardLayout`, `BattleClockPresentation`, `BattleCombatantLayout`, `BattleFieldVisualComposer`, `BattleMoveVisualStyleResolver`, `BattleTeamPreviewLayout`, `ShowdownBattleLayout`, `ShowdownBattleMovePresentation`) and their tests.
- Tests removed because the feature they covered is gone: `BattlePlaybackPolicyTest`, `BattleSceneEffectsContractTest`, the two low-memory playback tests, chrome-hiding and `MutationObserver` assertions, native status-card assertions in `ShowdownReplayPlaybackParityTest`, native scene lifecycle assertions in `MainActivityLifecycleContractTest`/`ShowdownSpriteCacheContractTest`; instrumented `MainActivityReadmeCaptureTest`, `MainActivityReplayPlaybackParityTest` and two native-scene tests in `ShowdownBattleLogRendererParityTest`. Contract tests that grep the page now read the Kotlin view plus the asset (assertions unchanged).
- 3c: instrumented tests now target the `local` build (`testBuildType 'local'`; the harness activity moved from `src/debug` to `src/local`; CI uses `compileLocalAndroidTestKotlin` / `connectedLocalAndroidTest`) so they can never reinstall or wipe the `.debug` app. Added `ShowdownBattleSceneViewTest` (view attached; minimal battle loads the page and syncs the log with no JS errors), run on the emulator. The finished battle scene now stays until the room is cleared.
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
