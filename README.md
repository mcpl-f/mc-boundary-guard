# Boundary Guard

Lightweight boundary guard for Minecraft servers that use `tg-bridge`.

It protects the wider world from anonymous griefing while trying to preserve vanilla experience for regular players. No WorldGuard setup is needed, and there is no need to maintain region protections just to keep new accounts near spawn.

## Purpose

This plugin reduces damage from griefers, throwaway accounts, and lazy anonymous players.

Unverified players stay inside a configurable sandbox spawn area. Inside this area they can move, build, break, test, and do whatever your server rules allow.

To leave the sandbox and access the wider world, a player must pass verification - by default, binding their Minecraft account to Telegram or Discord (configurable, see "Rules" below). This anti-grief account verification makes players less anonymous and raises the cost of griefing without changing normal gameplay for verified players.

The goal is simple: keep unverified accounts contained at spawn, let verified players play normally, and avoid heavy region protection plugins when all you need is a small boundary guard.

## Behavior

- If the player satisfies `rules.omit-restriction-when` (by default: Telegram
  linked, Discord linked, or simply standing inside the spawn radius),
  movement is unrestricted.
- Otherwise, the plugin blocks movement outside the allowed area.
- Normal worlds use distance from world spawn.
- Nether uses configured distance divided by 8.
- End uses distance from real `0,0`.
- A blocked player gets clickable actions for whichever ways of satisfying the
  condition are actually configured:
  - bind Telegram with `/tg account register`;
  - link Discord with `/discord link` (only if `ds-linking-check` is
    configured and DiscordSRV is installed);
  - teleport back to the spawn area with `/tgspawn` - always offered, since
    it's a way out for a player not ready to verify yet, not itself one of
    the ways to satisfy the condition.


## Restriction strategies

Unverified players can be restricted by any combination of (each a plain
on/off toggle - see "Rules" below for where the shared spawn-area radius
actually comes from):

- **`keep-on-spawn`** - blocks movement outside the spawn area.
- **`switch-2-adventure-mode`** - switches the player to Adventure Mode while
  outside the spawn area, leaving survival privileges intact near spawn.
- **`forbid-container-use`** - blocks opening/using containers (chests, furnaces,
  etc.) while restricted.

Combine `keep-on-spawn` with `switch-2-adventure-mode` for stricter anti-grief
protection: a hard movement block, plus Adventure Mode as a second line of
defense if the player ever ends up outside anyway.

## Rules

`rules.omit-restriction-when` decides when restrictions are lifted. Available conditions:

- **`tg-linking-check`** - met once the player has linked their Minecraft account
  to Telegram (via tg-bridge).
- **`ds-linking-check`** - met once the player has linked their Minecraft account
  to Discord (via DiscordSRV - see "Dependency" below). Configuring this
  without DiscordSRV installed fails on startup with a clear error.
- **`time-played-limit`** (`hours`) - met once the player's total playtime
  reaches the configured number of hours.
- **`in-spawn-radius`** (distance) - met while the player is within that
  distance of the spawn anchor (world spawn in normal worlds, divided by 8 in
  the Nether, measured from real `0,0` in the End). This is the single source
  of truth for "the spawn area": whichever restriction strategies are enabled
  above confine/switch the player based on this same radius, not a separate
  one of their own - there's nothing to configure per-strategy.

Combine them with `all` (every condition required) and `any` (at least one
required), nestable to any depth. The shipped default lets a player move
freely once *any* of these hold - linked to Telegram, linked to Discord, or
simply staying put near spawn:

```yml
rules:
  omit-restriction-when:
    any:
      - all:
          - time-played-limit:
              hours: 1.0
          - any:
              - tg-linking-check: true
              - ds-linking-check: true
      - in-spawn-radius: 2048

  otherwise-apply-restriction-strategies:
    # keep-on-spawn: true

    # Uncomment to also (or instead) switch to Adventure Mode while outside the area.
    switch-2-adventure-mode: true

    # Uncomment to block container use while restricted.
    # forbid-container-use: true
```

A stricter example - require both 2.5 hours played *and* (Telegram *or*
Discord linked), with no free pass just for staying near spawn:

```yml
rules:
  omit-restriction-when:
    all:
      - time-played-limit:
          hours: 2.5
      - any:
          - tg-linking-check: true
          - ds-linking-check: true
```

See `config.yml` for the full, commented default configuration.

## Configuration

Main settings live in `config.yml`:

- `rules.omit-restriction-when`'s `in-spawn-radius` leaf: the shared spawn-area
  radius in normal worlds (Nether divides it by 8, the End measures it from
  real `0,0`) - see "Rules" above.
- `message-cooldown-millis`: delay between repeated warning messages, in milliseconds.
- `restriction-refresh-interval-ticks`: how often restriction state is re-checked for online players.
- `telegram-recheck-cooldown-millis`: how long an unlinked Telegram lookup is cached before re-checking tg-bridge.
- `condition-reminder.interval-seconds` / `.enabled`: how often (and whether)
  a restricted player still idling near nothing in particular gets reminded
  what they need to satisfy.
- `default-locale`: fallback language for messages.

Translations live under `lang/<locale>.yml` (`lang/en.yml`, `lang/ru.yml` are
shipped), not in `config.yml` itself. Any `lang/*.yml` dropped into the
plugin's data folder is picked up automatically, no code change needed.

## Dependency

This plugin has a hard Bukkit dependency on `tg-bridge` - it must be installed
and loaded before this plugin.

`DiscordSRV` is a soft dependency: install it only if you want to use
`ds-linking-check`. Without it, the plugin runs normally as long as
`ds-linking-check` isn't referenced anywhere in `rules.omit-restriction-when`.
