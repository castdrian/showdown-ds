# Inconsistencies

Behavior that looks wrong or inconsistent, found while working. Newest first.

## Open

- **The `.debug` app reopens the same battle on every launch** (and after `adb install -r`). Persisted state (`active_battle_room` in saved state; `maintain_connection` / `reconnect_lobby_commands` in SharedPreferences) plus Showdown resending an unfinished battle on reconnect. Under investigation in worklog step 2.
- **Field-condition layers cover the native scene.** Fixed for terrain (commit `6129add8`); the weather layer is still visible by design and may tint sprites. Superseded by the WebView-core work.
- **Sprite fallback stalls on unreachable hosts.** HD sprites were tried from `pkparaiso.com` first with an 8 s connect timeout per candidate. Mitigated by a per-host cooldown (commit `65ecc761`); the HD path is being removed from battles.
- **Source-grepping "contract" tests** (for example in `ShowdownMoveEffectsContractTest`) assert on source text, not behavior, so they pass for code that does not work. Replace them when touching the area.
- **Emulator screen order is not guaranteed** without the patched Thor overlay; `adb screencap` only sees the primary display.
