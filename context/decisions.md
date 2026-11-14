# Decisions

Agreed with the developer before work started.

1. **Overlays live in the Pokémon tab.** The top screen is the pure Showdown web scene (display-only, no native overlays). Stat information (HP number and bar, level/gender, item, status, types, boosts for active Pokémon) goes on the team cards in the bottom-screen Pokémon tab, for both teams. Opponent cards only show revealed information. The existing detail view stays for ability, moves and stats.
2. **Safe testing.** Local Showdown server + bot opponents + a separate `local` build (`dev.adrian.showdown.local`). The developer's `.debug` install, saved login and stuck battle are never touched.
3. **Unfinished battles.** Lobby lists every unfinished battle the server reports with **Resume** and **Forfeit** (confirm dialog, `/forfeit`). Debug and local builds never auto-resume.
4. **Sprites.** Showdown's own sprites only in battles; the HD (pkparaiso) path is removed from battles. Pokédex and team views keep the sprite cache.
5. **Scope of native removal.** Only in-battle drawing is replaced. Lobby, team preview, team builder and command deck stay native. `BattleSession` stays. Old code is deleted once the WebView path has parity.
6. **Git.** `feature/webview-core`, small commits, no pushing.
7. **Testing.** Assume the Showdown WebView is correct; test only that the view exists and loads. Our own bridge tests stay. Tests are behavioral, named `Thing_Scenario_Expected`, no mocks.
8. **Repo essentials.** `AGENTS.md`, `build.sh --test`, `emulate.sh` now; ask before adding ktlint; Dockerfile last.
9. **Lightweight (no-WebView) mode removed.** WebView is required; a clear error shows if it is unavailable.
10. **Top screen is display-only** (no tap-to-inspect).
11. **Accessibility gap accepted.** The WebView scene has no accessibility tree; the same information stays reachable from the lower screen. Documented in `inconsistencies.md`.
12. **Embedded JS moves to assets.**
13. **Order of work:** scaffolding, reopening-battle fix + Resume/Forfeit, WebView-core top screen, Pokémon-tab overlays, cleanup/tests/lint. Run to completion unless blocked.
14. **`emulate.sh`:** default `local` variant, `--variant debug` opt-in, no hard-coded toolchain paths.
15. **Held-battle rule (step 2).** Rooms reported in the first `updatesearch` after a named login that the app did not expect are held for the prompt; later battles open normally. Release builds still reopen the room they had persisted as active (continuity after a process restart); debuggable builds never do.
16. **Dev login.** `./emulate.sh --login NAME` signs the `.local` build in as NAME on the local server (empty token, no password). It is gated on the local build so it cannot affect a real account or the `.debug` install.
