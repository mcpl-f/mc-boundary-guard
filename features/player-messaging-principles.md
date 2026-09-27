# Player-facing messaging principles

How this plugin talks to a restricted player, and why `services/messages/minecraft`
composes messages at send time instead of storing pre-written sentences (see
`features/restriction-condition-feedback.md` for the mechanics: `ReasonExpr`,
`reasons.*`/`restrictions.*`/`join.*`). This doc is the *why*, meant to guide any
future message this plugin adds, not just the ones that exist today.

## 1. Never tell a player to do something without showing them how

A restricted player is very often brand new to the server - that's the whole
scenario this plugin exists for. Telling them "link your Telegram account" and
leaving it there is close to useless: they have no way to know that means
running `/tg account register`, because they have no context for this server's
conventions yet.

Every actionable reason therefore carries three things together
(`reasons.<name>.label`/`.command`/`.hover`), not just a description:

- a **label** that includes the literal command as visible text (works even for
  a player on a client too old to render click actions, or one who just prefers
  reading over clicking),
- a **command** that runs on click, for anyone who can,
- **hover** text confirming what the click does before they commit to it.

A reason with nothing to click (`playtime` - "play longer" isn't a command) just
omits `command`/`hover` and stays plain text. The presence of `command` is
exactly what `MinecraftMessageService#has(base + ".command")` checks to decide
whether to render a reason as clickable at all - there's no separate "is this
reason actionable" flag to keep in sync, the data itself says so. This check
uses the configured default locale, not the receiving player's locale:
clickability is part of a reason's shared behavior, not a translation choice.
Keep command/hover availability consistent across supported locales so the same
reason is actionable for every player. Labels and hover text can still use the
player's locale and the normal fallback chain.

## 2. Never hardcode which path fixes the restriction

`rules.condition` is admin-configured, and there is essentially never exactly
one way to become unrestricted. The shipped default alone already has two
(`tg-linking-check` or `in-spawn-radius`); playtime and, later, Discord linking
can join in any `all`/`any` combination. A message that hardcodes "link
Telegram" as *the* fix is simply wrong on any server configured differently,
including the one this plugin ships with by default.

This is why the message is assembled from the live `Condition` tree
(`Condition#unmetReason` -> `ReasonExpr`) rather than written per scenario:
whatever reasons are actually unmet, combined with whichever operator
(`all`/`any`) actually combined them, is what gets shown - never an assumption
baked in ahead of time about which condition "the" server uses.

The same reasoning extends to *what's currently blocked*: a server can enable
`keep-on-spawn`, `switch-2-adventure-mode`, and `forbid-container-use` in any
combination, so a message naming only one of them (e.g. always saying "leave
the spawn area" even when container use is also blocked) would be incomplete
on a server running more than one. See "showing every active restriction" below
for how that's handled for a reminder that isn't tied to one specific action.

## 3. Match how much the message says to how the player triggered it

Not every message-sending moment has the same amount of context available, and
showing more than is relevant is its own kind of noise:

- **A reactive message, triggered by one specific blocked action**
  (`sendBoundaryBlocked` on a blocked move, `sendContainerBlocked` on a blocked
  container open) names *that one* restriction - the player just ran into it,
  naming five others they didn't touch would bury the relevant one.
- **The Adventure Mode event handlers** (`onGameModeChange`, `onArmSwing`,
  `onBlockPlace` in `RestrictionEventListener`) are, by construction, only ever
  about Adventure Mode - they're gated on `GameMode.ADVENTURE`. So they always
  pass a single, hardcoded `RestrictionEffect.INTERACT_FREELY` - not because the
  code is lazy about it, but because that really is the one thing relevant to
  what just happened.
- **`ConditionReminderJob`**, the idle-player reminder, has no specific action
  to react to at all - it just fires on a timer. There, the right amount of
  information is *everything currently active*
  (`RestrictionService#activeEffects()`, one `RestrictionEffect` per enabled
  `Restriction` strategy), joined with `join.and`, since an idle player
  legitimately benefits from knowing the whole current picture rather than an
  arbitrarily chosen slice of it.

The rule of thumb: a message tied to one action reports that one thing; a
message tied to no action in particular reports everything currently true.

## 4. Don't model an escape hatch as if it were a reason

A `reasons.*` entry (principle 1) and `/tgspawn` look superficially alike -
both are clickable, both show a runnable command. They're not the same kind
of thing, and briefly conflating them (`ConditionReason.RETURN_TO_SPAWN`, see
`features/restriction-condition-feedback.md`'s "Post-merge correction")
produced a circular message: offering "teleport to spawn" as the fix for
"you can't leave spawn" is nonsense, precisely because that's the one message
that fires from the exact situation `/tgspawn` addresses.

The distinguishing question is *durability*: does taking this action leave you
unrestricted, or just move you somewhere the restriction doesn't currently
apply? Linking Telegram or reaching a playtime threshold durably satisfies
`rules.condition` - once true, it stays true. Standing inside the spawn area
does not - it's true only while you don't move, and the moment you try to
leave you're blocked the same way again. Only the former belongs in the
reason/restriction sentence as an alternative worth naming. `/tgspawn` remains
a separate escape action, appended after a boundary warning, but reuses
`reasons.return-to-spawn` label/command/hover instead of duplicating config
under separate `spawn-button` and `spawn-hover` keys.
