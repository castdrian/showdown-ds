# Inconsistencies

Behavior that looks wrong or inconsistent, found while working. Newest first.

## Open

- **Dev login can fall back to a guest** on a no-security local server: when the app reconnects (for example to search) while the server still holds the previous connection for the same name, the empty-token `/trn` is refused and the user stays `Guest N`. Real accounts authenticate with a token and merge connections, so this only affects `--login`.
- **Showdown's `games` map marks some battles with a trailing `*`** (`allowRenames` is false, i.e. ladder-style games), e.g. `[Gen 9] Random Battle*`. The lobby's room list shows the raw string; only the unfinished-battle prompt strips it.
- **`|updatesearch|` arrives before the room's `|init|battle`** for a reconnecting account, so anything built from the `games` list alone has no opponent name yet. The prompt waits for the `|title|` packet.
- **Guests cannot be tested for unfinished battles**: a reconnecting guest gets a new identity, so only named users (real accounts, or `--login NAME` on the local server) keep battles across reconnects.
- **Local server needs `noipchecks`** for ladder searches between clients on one machine; without it two searches never match and nothing reports why.
- **The emulator's audio server crashed once** (`libaudioflinger` native trace in the crash buffer, not the app). Environmental.
- **Field-condition layers cover the native scene.** Fixed for terrain (commit `6129add8`); the weather layer is still visible by design and may tint sprites. Superseded by the WebView-core work.
- **Sprite fallback stalls on unreachable hosts.** HD sprites were tried from `pkparaiso.com` first with an 8 s connect timeout per candidate. Mitigated by a per-host cooldown (commit `65ecc761`); the HD path is being removed from battles.
- **Source-grepping "contract" tests** (for example in `ShowdownMoveEffectsContractTest`) assert on source text, not behavior, so they pass for code that does not work. Replace them when touching the area.
- **Emulator screen order is not guaranteed** without the patched Thor overlay; `adb screencap` only sees the primary display.

## Resolved

- **The `.debug` app reopened the same battle on every launch.** Fixed in step 2; see `worklog.md`.
