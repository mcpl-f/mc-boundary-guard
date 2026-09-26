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

**Trigger points** (`RestrictionEventListener`):
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

## Known limitations / follow-ups

- `condition-playtime`'s text is static ("play longer") - it doesn't interpolate
  the actual configured `hours` value. Adding that needs a small templating step
  in `MinecraftMessageService`; skipped for this pass to keep the change small.
- The `BlockBreakEvent`/`BlockPlaceEvent` behavior in Adventure Mode (does the
  event still fire when vanilla's `CanDestroy` check would block it anyway?) is
  based on general Bukkit/Spigot knowledge, not verified against a running
  1.16.4 server - worth confirming manually if the in-game reminder doesn't
  appear when expected.
- No test exercises the new `RestrictionEventListener` handlers directly (the
  three new `@EventHandler` methods) - covered indirectly through the
  `Condition`/`MinecraftMessageService` unit tests instead, since a
  `PlayerGameModeChangeEvent`/`BlockBreakEvent`-level test would need a fuller
  Bukkit event mock than what's set up so far.
