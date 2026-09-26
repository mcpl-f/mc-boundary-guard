# Restriction condition feedback

Implements two PR #1 review comments (`config.yml:48`, `config.yml:31`,
2026-09-26): Adventure Mode gave a restricted player zero explanation, and there
was no service to describe *what* a player still needs to satisfy based on the
actual configured `rules.condition` tree.

## Problem

- `AdventureModeRestriction.refresh()` called `player.setGameMode(ADVENTURE)`
  silently. `SpawnBoundaryRestriction` and `ContainerUseRestriction` both trigger
  a message on block (`sendBoundaryBlocked` / `sendContainerBlocked`); Adventure
  Mode had no equivalent, so a player restricted only by that strategy had no
  idea why they suddenly couldn't build/break.
- The existing messages (`blocked`, `container-blocked`) hardcode "link
  Telegram," but `rules.condition` is a generic `all`/`any` tree - it can require
  playtime, playtime *and* Telegram, or (once Discord exists) Telegram *or*
  Discord. The static message text has no relationship to what's actually
  configured, so it can be simply wrong for any config other than
  Telegram-only.

## Design

**`Condition` reports a neutral reason, not a message key.** The first version of
this had `Condition.reasonKey()` return the literal `messages.yml` key
(`"condition-tg-linking"`) directly - which leaked a presentation-layer detail
(the config schema's key-naming convention) into the rule-evaluation layer, and
would have shown the raw key string to a player if the two ever drifted apart.
Fixed by introducing `ConditionReason`, a plain enum with no knowledge of
`config.yml`:

```java
public enum ConditionReason { TELEGRAM_LINKING, PLAYTIME }

public interface Condition {
    boolean isMet(Player player);

    default Optional<ConditionReason> reason() { ... }          // this leaf's reason
    default List<ConditionReason> unmetReasons(Player player) { ... } // walks the tree
}
```

- Leaf conditions (`TelegramLinkedCondition`, `MinimumPlaytimeCondition`) only
  override `reason()` - one line each. The interface default handles turning
  that into `unmetReasons` by checking `isMet` first.
- `ConditionApplier` overrides `unmetReasons` directly, since it must recurse
  instead of reporting a single reason: for `all`, every unmet child is
  collected; for `any`, nothing is reported once one child is met, otherwise
  every child is collected as a valid alternative.
- No natural-language "X and Y" / "X or Y" composition is attempted - the result
  is a flat, deduplicated list of reasons, and the message layer just joins them
  one-per-line. Simpler and more robust than trying to generate
  grammatically-correct localized boolean text, at the cost of not being able to
  say "either X or Y" as a single sentence.
- `services/conditions` now has zero knowledge that `messages.*` even exists in
  `config.yml`; the mapping from `ConditionReason` to an actual key
  (`messageKeyFor`, a private `switch` in `MinecraftMessageService`) is the only
  place that connects the two, and it lives entirely in the messages package.

**`RestrictionService.unmetReasons(Player)`** - thin passthrough to the
configured `Condition`, so callers don't need to reach into it directly.

**`MinecraftMessageService.sendConditionReminder(Player, List<ConditionReason>)`**
- maps each reason to its key, resolves it through the existing locale-aware
`get(player, key)`, joins with `\n`, and shares the same per-player cooldown
(`tryWarn`) as the other warning messages (a container-blocked message resets
the same cooldown a condition reminder would use, and vice versa - intentional,
avoids spamming a restricted player from multiple message types in quick
succession).

**Trigger points** (`RestrictionEventListener`), as of the initial version below
(see "Block-break notification never fired" for what changed):
- `PlayerGameModeChangeEvent`, `ignoreCancelled = true`, filtered to
  `getNewGameMode() == ADVENTURE` - fires once at the moment
  `AdventureModeRestriction` actually switches a player in (not on every refresh
  tick, since `refresh()` only calls `setGameMode` when the mode differs).
- `BlockBreakEvent` / `BlockPlaceEvent`, `EventPriority.MONITOR`, **not**
  `ignoreCancelled` - registered without ignoring cancelled events on purpose:
  vanilla's own Adventure Mode `CanDestroy`/`CanPlaceOn` check may cancel the
  interaction before our handler runs, and we still want to notify in that case.
- Both paths are gated on `player.getGameMode() == ADVENTURE` (not just "is
  restricted"), so a player restricted only by `keep-on-spawn` - who is meant to
  freely build/break inside their sandbox per the plugin's own stated purpose -
  never gets this reminder while doing exactly what they're allowed to do.

New `messages.*` keys (`en`/`ru`): `condition-tg-linking`, `condition-playtime`.

## Post-merge fix: StackOverflowError on a live server

The very first live test (server logs, not this test suite) hit a real
`StackOverflowError` on the server thread. Root cause: `CraftPlayer#setGameMode`
fires `PlayerGameModeChangeEvent` *synchronously and before* the player's game
mode field is actually updated. `onGameModeChange` reacted to that event by
calling `RestrictionService#refresh(player)` - which re-ran every
`Restriction`, including `AdventureModeRestriction`, which checked
`player.getGameMode() != ADVENTURE` (still true - the event fires before the
mode is applied) and called `setGameMode(ADVENTURE)` again, firing the event
again, forever:

```
setGameMode -> PlayerGameModeChangeEvent -> onGameModeChange -> refresh()
  -> AdventureModeRestriction.refresh() -> setGameMode -> (repeat)
```

The actual bug was fusing two different concerns into one method:
`RestrictionService#refresh` was both "is this player restricted" (a pure
read) and "apply every restriction strategy's side effects" (mutates game
state, cancellable-event-adjacent). A listener reacting to a side effect by
calling the same side-effecting method it's nested inside of was never going
to be safe.

**Fix:** added `RestrictionService#isRestricted(Player)` - `!accessCondition.isMet(player)`,
no strategies touched, no side effects - and switched all three new handlers
(`onGameModeChange`, `onBlockBreak`, `onBlockPlace`) to call that instead of
`refresh()`. `RestrictionEventListenerTest` now pins this down directly:
every reminder-path test asserts `verify(restrictions, never()).refresh(any())`.

This also resolves what was flagged as an open question below (the
`BlockBreakEvent`/`BlockPlaceEvent`-in-Adventure-Mode behavior *was* verified
against a running server, just by way of crashing it) - `refresh()` is never
called from any of these three handlers now, so it doesn't matter whether
vanilla cancels the block event first or not.

## Post-merge fix: block-break notification never fired

Second live test, once the crash was fixed: breaking a block in Adventure Mode
still sent no reminder at all. Researched rather than guessed at this one (see
sources below) - in vanilla, a player's held item is checked against its
`CanDestroy` NBT tag *before* the server ever constructs a `BlockBreakEvent`,
so a denied break attempt has no event for `onBlockBreak` to catch; the
`ignoreCancelled = true` reasoning above only covers a break that reaches
Bukkit and gets cancelled there, not one vanilla rejects earlier than that.
This is a widely-reported Bukkit/Spigot limitation, and the community's
worked-around fix is the same for the analogous `PlayerInteractEvent`
`LEFT_CLICK_BLOCK` case: listen for `PlayerAnimationEvent` instead, which is
the arm-swing animation itself and fires unconditionally on left-click,
regardless of gamemode restrictions.

**Fix:** replaced `onBlockBreak(BlockBreakEvent)` with
`onArmSwing(PlayerAnimationEvent)`. Confirmed `PlayerAnimationEvent` is
standard Bukkit/Spigot API (not Paper-only), so no new dependency. Trade-off:
it can't distinguish "swung at a block" from "swung at air," but that's an
acceptable cost for getting a signal at all. `BlockPlaceEvent` was left as-is -
placing likely has the same `CanPlaceOn` gap, but that's unconfirmed (only the
break case was actually observed failing), so it isn't worth guessing at a fix
for a bug that hasn't been demonstrated yet.

Sources:
- [Solved - How to detect player left click block in Adventure Mode | SpigotMC](https://www.spigotmc.org/threads/how-to-detect-player-left-click-block-in-adventure-mode.258913/)
- [PlayerAnimationEvent (Spigot-API)](https://hub.spigotmc.org/javadocs/spigot/org/bukkit/event/player/PlayerAnimationEvent.html)

## Colored messages

All player-facing chat messages now use exactly two colors: red for a blocked/
restricted status, green for a success or helpful action. Changed:
`sendContainerBlocked` and `sendConditionReminder` (previously uncolored) to
red; the `/tgspawn` clickable button (previously yellow) to green, matching the
Telegram-bind button; and `TgSpawnCommand`'s `already-in-area`/`teleported`
(previously uncolored/gold) to green, `only-player` (previously uncolored) to
red.

## Idle-player reminder job

The gamemode-change and arm-swing/block-place triggers only fire when a
restricted player does something. A player standing still in Adventure Mode -
not moving, not clicking anything - got no reminder at all. Added
`ConditionReminderJob`, scheduled independently of `RestrictionRefreshJob` on
its own configurable period (`condition-reminder-interval-seconds`, default 2s,
converted to ticks by `BoundaryGuardConfig#conditionReminderIntervalTicks`),
that reminds every currently-restricted online player on that cadence. Uses
`RestrictionService#isRestricted`, not `refresh`, for the same reason as the
event-listener handlers above: it only needs to read condition state, never
apply a strategy's side effects.

`BoundaryGuardRuntime` now tracks and cancels two tasks (`refreshTask`,
`conditionReminderTask`) instead of one.

## Post-merge redesign: the reminder job still nagged players who weren't doing anything wrong

`ConditionReminderJob` above gated on `RestrictionService#isRestricted`, which
only asked `rules.condition` (Telegram linking / playtime) - it had no idea
whether the player was inside the spawn area. A player who hadn't linked
Telegram but was calmly standing at spawn (fully within their rights, per this
plugin's own "build/break/test at spawn" purpose) got pinged every 2 seconds
anyway, because part of "am I actually being restricted right now" lived in the
`Restriction` strategies' own location checks (`AdventureModeRestriction`'s
boundary/`everywhere`, `SpawnBoundaryRestriction`'s boundary), not in
`rules.condition` at all.

**Fix:** moved location into the condition tree itself, as a new leaf,
`in-spawn-radius` (`InSpawnRadiusCondition`) - met while the player is within
that distance of the anchor, same Normal/Nether/End rules as
`domain/BoundaryArea` always used. The default condition became:

```yaml
condition:
  any:
    - tg-linking-check: true
    - in-spawn-radius: 2048
```

Once location is part of `isMet`, `isRestricted` (and therefore every reminder
path) is correct for free - a player standing inside the area is simply not
restricted, no special-casing needed anywhere in the reminder code.

This also let both strategies drop their own location logic entirely, since
`restricted` (computed once, from the condition) already accounts for it:
`AdventureModeRestriction` lost its `boundaryArea`/`everywhere` fields and
`refresh()` is now a two-line reaction to the `restricted` flag; config went
from `switch-2-adventure-mode: {everywhere, only-outside-of-radius}` to a plain
`switch-2-adventure-mode: true`, and `keep-on-spawn: {radius}` to
`keep-on-spawn: true`. `BoundaryGuardConfig#spawnRadius()` now finds
`in-spawn-radius` by walking the (possibly nested, `all`/`any`) condition tree
rather than reading a strategy-specific key, so the one number that defines
"the spawn area" can't drift out of sync between what decides *if* you're
restricted and what physically enforces it.

**The correctness trap this created, and its fix:** `RestrictionService`
already had `if (!refresh(player)) return true;` as the first line of
`allowsMovement` - safe under the *old* condition (Telegram/playtime only,
location-independent), because "unrestricted" meant "unrestricted everywhere."
Once location entered the condition, that stopped being true: a player
standing at `distance=90` inside a `radius=100` area reads as *not restricted*
(current position satisfies `in-spawn-radius`) - but the short-circuit would
then wave through a move to `distance=110`, since it never even looked at
where they were going. `SpawnBoundaryRestriction` would never be consulted for
the one move that actually needed it.

Fixed by adding `Condition#isMet(Player, Location)` (default: ignore the
location, delegate to `isMet(Player)` - correct for every location-independent
condition), overridden by `InSpawnRadiusCondition` to check the given location
instead of `player.getLocation()`, and propagated recursively by
`ConditionApplier`. `RestrictionService#allowsMovement` now gates on
`accessCondition.isMet(player, destination)` - "would this destination be
fine" - instead of the player's current spot, while still calling `refresh()`
unconditionally first so current-position side effects (gamemode switching)
still happen exactly as before. `RestrictionServiceTest` pins the exact
scenario: standing inside, moving to just outside, must be blocked.

`allowsContainerUse`/`blocksContainerUse` were not touched - container
blocking was never location-dependent, so the original current-position
short-circuit there is still correct.

## Known limitations / follow-ups

- `condition-playtime`'s text is static ("play longer") - it doesn't interpolate
  the actual configured `hours` value. Adding that needs a small templating step
  in `MinecraftMessageService`; skipped for this pass to keep the change small.
- `BlockPlaceEvent` may have the same Adventure Mode event-suppression gap that
  `BlockBreakEvent` had - not yet confirmed either way.
