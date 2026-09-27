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

## Post-merge redesign: messages stopped being hardcoded per scenario

The `condition-reminder.tg-linking` message was a fully pre-written sentence
that duplicated content the boundary-blocked message already had properly
formatted (`bind-button`/`bind-hover`, clickable *and* showing the literal
command as text, for clients without hover support). Any new message that
needed to say "link Telegram" would have meant writing that sentence a third
time, and the three existing messages (`blocked`, `container-blocked`,
`condition-reminder.*`) had no structural relationship to each other at all,
despite all three fundamentally saying the same shape of thing: *some
reason(s), joined, followed by what they unblock*.

**Redesign:** messages.yml no longer stores full sentences. It stores three
kinds of small, reusable pieces, and `MinecraftMessageService` composes the
final message at send time:

- **`reasons.*`** - one entry per `ConditionReason` (`tg-linking`, `playtime`).
  A `label` always; `command` and `hover` only for a reason that's an actual
  runnable command - `boundary-blocked`'s old bind-button *was* this pattern
  (click for new clients, visible command text as a fallback for old ones), now
  it's the only place that pattern is built, reused for every message that
  might need to mention linking Telegram.
- **`restrictions.*`** - what's currently blocked, one phrase per calling
  context (`leave-spawn`, `use-containers`, `interact-freely`).
- **`join.*`** - connector words: `and`/`or` glue multiple reasons together,
  matching whichever `ConditionApplier.Operator` actually produced them (not
  hardcoded to one or the other); `before`/`so-that` attach the restriction -
  `before` for a reactive "you just got blocked" message
  (boundary-blocked/container-blocked), `so-that` for a proactive reminder
  (condition-reminder). Each connector carries its own language's grammar glue
  (English `join.so-that: "so that you can"`, Russian `join.so-that: "чтобы"` -
  Russian's bare infinitive already reads correctly after "чтобы," English
  needs "you can" inserted to stay grammatical).

**The `all`/`any` structure had to survive, not get flattened.** The previous
`unmetReasons(Player): List<ConditionReason>` discarded whether reasons came
from an `all` or an `any` group - fine when the plan was "join with newlines,"
wrong once "and" vs "or" needs to be picked correctly. Replaced with
`Condition#unmetReason(Player): Optional<ReasonExpr>`, where `ReasonExpr` is a
tiny two-case tree (`services/conditions/ReasonExpr.java`):

```java
sealed interface ReasonExpr {
    record Leaf(ConditionReason reason) implements ReasonExpr {}
    record Group(ConditionApplier.Operator operator, List<ReasonExpr> children) implements ReasonExpr {}
}
```

A leaf condition's default just wraps its own `reason()` in a `Leaf` (or
`Optional.empty()` if met); `ConditionApplier` overrides it to recurse into
its children and wrap them in a `Group` carrying its own operator - collapsing
to a bare `Leaf` when only one child survives, so a single reason is never
wrapped in a pointless one-element group. `RestrictionService#unmetReasons`
was renamed to `unmetReason` (singular - it's one expression tree, not a list)
and now returns `Optional<ReasonExpr>`.

**All three send methods now build a `TextComponent` tree via
`player.spigot().sendMessage(...)`, not a plain string.** They have to: a
reason can carry its own click/hover action, so a plain `String` (as
`sendConditionReminder` used before) can no longer represent the full message.
`sendBoundaryBlocked(Player, Optional<ReasonExpr>)` and
`sendContainerBlocked(Player, Optional<ReasonExpr>)` both gained the
`Optional<ReasonExpr>` parameter to match; every call site
(`BoundaryMoveListener`, `RestrictionEventListener`, `ConditionReminderJob`) now
passes `restrictions.unmetReason(player)` through.

Testing a component tree needed `BaseComponent.toLegacyText(...)` (public
*static* on `BaseComponent`, confirmed via `javap` against the actual
bungeecord-chat 1.16-R0.3 jar this project resolves - it is *not* on
`TextComponent`, and the *instance* `toLegacyText` is `protected`), with a
`§.` strip afterward so a plain assertion reads the composed sentence, not
its formatting codes. At the time this was written, this codebase's colors
were all raw `ChatColor.X + text` string concatenation rather than proper
`.setColor()` calls - see "Post-merge correction #2" below for why that
turned out to be a real in-game bug this exact strip-and-compare pattern
couldn't have caught either way. `MinecraftMessageServiceTest` captures
via `Player.Spigot` (also mocked - Mockito's inline mock maker handles this
fine since it never needs to invoke a real constructor) and asserts both the
composed text and, separately, `getClickEvent()`/`getHoverEvent()` on the
captured component tree for the clickable case.

## Post-merge additions: return-to-spawn as a reason, and per-context effect scope

**The `return-to-spawn` half of this section was reverted, then re-added in a
narrower form - see "Post-merge correction" and "Post-merge correction #2"
below.** Left in place rather than rewritten so the reasoning that led to it,
the reasoning that undid it, and the reasoning that brought part of it back
are all on record.

Two follow-ups, driven by `features/player-messaging-principles.md`'s two
rules (never send an instruction without showing how, never assume there's
only one fix) and by re-reading what a message is actually for depending on
how it was triggered.

**`in-spawn-radius` now reports a reason.** It was the one condition
deliberately left with an empty `reason()` - the reasoning at the time was
"being in an area isn't an action." That undersold it: walking or
teleporting back to spawn is exactly as real a fix as linking Telegram, so it
should show up as an alternative the same way. Added `ConditionReason
.RETURN_TO_SPAWN`, `InSpawnRadiusCondition#reason()` returns it, and
`reasons.return-to-spawn` (label/command `/tgspawn`/hover) uses the exact same
clickable pattern as `tg-linking`. This let a whole special case disappear:
`sendBoundaryBlocked` used to always hardcode-append a `spawn-button`
regardless of which reasons were actually unmet; now "return to spawn" only
shows up when `in-spawn-radius` is genuinely part of the condition (which it
is by default), through the same mechanism as every other reason, and the
top-level `spawn-button`/`spawn-hover` config keys are gone.

**A reminder now says either one restriction or all of them, depending on why
it's firing.** `sendConditionReminder` always described exactly one thing
blocked (`restrictions.interact-freely`), even from `ConditionReminderJob`,
which fires on a timer with no specific action to react to - so if a server
had, say, both `keep-on-spawn` and `forbid-container-use` enabled, the idle
reminder only ever mentioned the Adventure-Mode-flavored one, regardless of
what was actually active. Added `RestrictionEffect` (mirrors `ConditionReason`
on the restrictions side: `LEAVE_SPAWN`/`USE_CONTAINERS`/`INTERACT_FREELY`) and
`Restriction#effect()`, so `RestrictionService#activeEffects()` can list one
per currently-enabled strategy. `sendConditionReminder` takes
`List<RestrictionEffect>` now, joined with `join.and` when there's more than
one (they're all simultaneously true - never alternatives the way reasons from
an `any` group are). Callers pick the scope that matches how they were
triggered: `RestrictionEventListener`'s three Adventure-Mode handlers
(`onGameModeChange`/`onArmSwing`/`onBlockPlace`) still hardcode
`List.of(INTERACT_FREELY)` - by construction they only ever fire because of
Adventure Mode, so that's genuinely the one relevant thing - while
`ConditionReminderJob` passes `restrictions.activeEffects()`. The rule of
thumb this landed on: a message tied to one action reports that one thing; a
message tied to no action in particular reports everything currently true.

## Post-merge correction: return-to-spawn reverted, and a gerund/infinitive split

Two problems, found by testing the actual rendered sentences rather than just
the plumbing that produces them.

**Treating `/tgspawn` as a `ConditionReason` was conceptually wrong, and
concretely circular for its most common trigger.** Every other reason
(linking Telegram, accumulating playtime) *durably* changes whether
`rules.condition` is met - once satisfied, it stays satisfied. Standing inside
`in-spawn-radius` doesn't: it's true only as long as you don't move, and the
instant you try to leave again you're right back to being blocked. Modeling it
as a reason presented a reversible position as if it were equivalent to an
actual verification method, and `sendBoundaryBlocked` - which fires *because*
someone tried to leave the spawn area - made the confusion impossible to miss:
`"[Teleport to spawn - /tgspawn] before you can leave the spawn area"` offers
"go to spawn" as the fix for "you can't leave spawn," which is circular
nonsense. `/tgspawn` was never meant to be a way to *satisfy* the condition
the same way linking Telegram is; it's an escape hatch for a player not ready
to deal with verification right now - an addition to "just leave," available
regardless of which reason(s) are actually unmet, not one of the reasons
itself.

Reverted: `ConditionReason` is back to just `TELEGRAM_LINKING`/`PLAYTIME`;
`InSpawnRadiusCondition#reason()` no longer overrides the default (empty) one;
`reasons.return-to-spawn` is gone from config. `sendBoundaryBlocked` goes back
to always appending a separately-built `/tgspawn` button (`spawn-button`/
`spawn-hover` config keys, restored) after the reason/restriction sentence -
outside the dynamic reason composition entirely, so it can never join, compete
with, or get confused for a genuine reason.

**The composed English used one connector form where the original hand-written
text used two.** `"...before you can leave the spawn area"` (modal + bare
infinitive) is grammatically fine on its own, but it's a different, clunkier
construction than what it replaced - the original text was `"...before leaving
spawn"` (gerund), and forcing every restriction phrase through the same
"before you can X" template (needed at the time to also serve `"so that you
can X"`) lost that. The two connectors don't actually take the same form:
"before" wants a gerund right after it, "so that you can" wants a bare
infinitive - one uniform phrase per restriction can't satisfy both.

Fixed by giving each `restrictions.*` entry two forms instead of one -
`gerund` (paired with a bare `join.before`, e.g. `"before leaving the spawn
area"`) and `infinitive` (paired with `join.so-that`, unchanged: `"so that you
can leave the spawn area"`). `sendBoundaryBlocked`/`sendContainerBlocked` (the
reactive "before" messages) ask for the gerund form; `sendConditionReminder`
(the proactive "so that" reminder) asks for the infinitive. Russian doesn't
need the split - both `перед тем как` and `чтобы` already take a bare
infinitive - so its `gerund`/`infinitive` pairs just repeat the same text; the
schema stays uniform across locales even though only English needs two forms.

## Post-merge correction #2: return-to-spawn re-added with a narrower exclusion, and colors that only worked in the test helper

Two more problems, again found by testing the actual rendered/in-game message
rather than the plumbing that produces it.

**The previous correction conflated two different things under one
"never a reason" rule.** `/tgspawn` the command (a manual override a player
runs) and `in-spawn-radius` the condition (one of the `any` branches
`rules.condition` actually accepts) got reverted together, but only the first
one is genuinely never a reason - it doesn't satisfy anything, it just moves
the player somewhere the restriction doesn't currently apply. The second one
*does* satisfy `rules.condition`, exactly the same way linking Telegram does;
the previous correction's "not durable" framing was true but led to throwing
out something that was still a legitimate alternative to mention everywhere
except the one place it's circular. Re-added `ConditionReason.RETURN_TO_SPAWN`
and `InSpawnRadiusCondition#reason()`, plus `reasons.return-to-spawn`
(label/`/tgspawn`/hover, same clickable pattern as `tg-linking`) - shown as a
normal "or" alternative in `sendContainerBlocked` and `sendConditionReminder`,
where "or return to spawn, so that you can use containers" is simply correct.

The circularity the first correction actually needed to fix only exists in
one specific spot: `sendBoundaryBlocked` always describes the `leave-spawn`
restriction, and *there* "return to spawn" is still the one thing that can't
be offered as a fix - it names the exact action the player is currently being
stopped from doing. `MinecraftMessageService#reasonMessage` now excludes
`RETURN_TO_SPAWN` from the reason tree specifically when `effects` contains
`RestrictionEffect.LEAVE_SPAWN` (`dropReturnToSpawn`, recursing through a
`Group` and collapsing it the same way `ConditionApplier` would if that
branch had never been unmet), regardless of which send method produced that
effects list - so a reminder that happens to mention `leave-spawn` alongside
other restrictions is covered too, not just the boundary-blocked message. If
`in-spawn-radius` were the *only* unmet reason (an admin-configured
`rules.condition` with no Telegram/playtime alternative at all), dropping it
would leave nothing to say; that edge case falls back to showing it anyway
rather than sending no reason at all. The always-appended `/tgspawn`
escape-hatch button in `sendBoundaryBlocked` (`spawn-button`/`spawn-hover`) is
unaffected by any of this - it's still built completely separately from the
reason composition, per the first correction.

**Colors were being set by embedding a legacy `§`-code into a component's own
text, which a real client never reinterprets, but `toLegacyText()` does -
so every test read the message back through exactly the blind spot that hid
the bug.** `new TextComponent(ChatColor.RED.toString())` (and every
`ChatColor.X + " " + text` join word alongside it) puts the raw color
character *inside* that node's `text` string instead of setting its
structured `color` field. `BaseComponent#getColor()` - which `toLegacyText()`
uses to decide what to re-emit - walks up to a parent's color when a node's
own is unset, so on the *actual* tree here (root's structured color never
set, every child's color also never set) it always resolved to white
end-to-end, and `toLegacyText()` therefore emitted the right-looking `§c`/`§a`
sequences anyway, by re-deriving them from... nothing, coincidentally
matching the embedded codes' positions closely enough that
`MinecraftMessageServiceTest`'s `plainText()` helper (which strips `§.`
before asserting) never noticed either way. None of that server-side
round-tripping is what a real client does with a JSON chat packet: it renders
each node's own resolved color, and a `§c` sitting inside one node's `text` is
just an unprinted character in that one node, not a color switch other nodes
pick up. Net effect in game: the restriction phrase and any plain-text reason
(no command, like `playtime`) rendered in the client's default white, not
red, because nothing had ever set their *structural* color or that of an
ancestor.

Fixed by using `TextComponent#setColor` everywhere instead of embedding
`ChatColor` into text: the message root gets `setColor(RED)` once, and
everything that doesn't need its own color (join words, the restriction
phrase, a plain-text reason) now inherits it for real, the same way a
clickable reason's explicit `setColor(GREEN)` overrides it locally without
needing to be "reset" afterward for whatever comes next - a fix that also
deleted the old (and, per the above, never-accurate) comment about green
"bleeding into" a following join word, since sibling nodes were never
affected by each other to begin with. `MinecraftMessageServiceTest` gained
direct `getColor()` assertions (`restrictionPhraseInheritsRedFromThe
MessageRootInsteadOfStayingUncolored`, `clickableReasonIsGreenWhileTheRest
OfTheMessageStaysRed`) precisely because the existing `plainText()`-based
assertions are structurally incapable of catching this class of bug.

## Known limitations / follow-ups

- `reasons.playtime.label`'s text is static ("play longer on the server") - it
  doesn't interpolate the actual configured `hours` value. Adding that needs a
  small templating step in `MinecraftMessageService`; skipped for this pass to
  keep the change small.
- `BlockPlaceEvent` may have the same Adventure Mode event-suppression gap that
  `BlockBreakEvent` had - not yet confirmed either way.
- Nested `Group`s (e.g. `all: [A, any: [B, C]]`) join flat, without
  parenthesization - rendered as "A and B or C," which is ambiguous about
  precedence. Not disambiguated since the shipped config and README examples
  never nest more than one level deep in practice; a config that did would read
  a bit oddly but not incorrectly (all children still get listed).
