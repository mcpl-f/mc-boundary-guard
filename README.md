# Boundary Guard

Lightweight boundary guard for Minecraft servers that use `tg-bridge`.

It protects the wider world from anonymous griefing while trying to preserve vanilla experience for regular players. No WorldGuard setup is needed, and there is no need to maintain region protections just to keep new accounts near spawn.

## Purpose

This plugin reduces damage from griefers, throwaway accounts, and lazy anonymous players.

Unverified players stay inside a configurable sandbox spawn area. Inside this area they can move, build, break, test, and do whatever your server rules allow.

To leave the sandbox and access the wider world, a player must pass Telegram verification by binding their Minecraft account to a Telegram account. This anti-grief account verification makes players less anonymous and raises the cost of griefing without changing normal gameplay for verified players.

The goal is simple: keep unverified accounts contained at spawn, let verified players play normally, and avoid heavy region protection plugins when all you need is a small boundary guard.

## Behavior

- If player is already bound to Telegram, movement is unrestricted.
- If player is not bound, plugin blocks movement outside allowed area.
- Normal worlds use distance from world spawn.
- Nether uses configured distance divided by 8.
- End uses distance from real `0,0`.
- Blocked player gets clickable actions:
  - bind Telegram account with `/tg account register`;
  - teleport back with `/tgspawn`.


## Restriction strategies

Unverified players can be restricted by any combination of:

- **`keep-on-spawn`** - blocks movement outside a configured radius.
- **`switch-2-adventure-mode`** - switches the player to Adventure Mode once they
  leave a configured radius (or everywhere, regardless of location), leaving
  survival privileges intact near spawn.
- **`forbid-container-use`** - blocks opening/using containers (chests, furnaces,
  etc.) while restricted.

Combine `keep-on-spawn` with `switch-2-adventure-mode` for stricter anti-grief
protection: a hard movement block, plus Adventure Mode as a second line of
defense if the player ever ends up outside anyway.

## Rules

`rules.omit-restriction-when` decides when restrictions are lifted. Available conditions:

- **`tg-linking-check`** - met once the player has linked their Minecraft account
  to Telegram (via tg-bridge).
- **`time-played-limit`** (`hours`) - met once the player's total playtime
  reaches the configured number of hours.
- **`ds-linking-check`** - not implemented yet; reserved for a future Discord
  linking check.

Combine them with `all` (every condition required) and `any` (at least one
required), nestable to any depth:

```yml
rules:
  omit-restriction-when:
    all:
      - time-played-limit:
          hours: 2.5
      - any:
          - tg-linking-check
          - ds-linking-check # not implemented yet, just for example

  otherwise-apply-restriction-strategies:
    keep-on-spawn:
      radius: 2048.0

    # Uncomment to also (or instead) switch to Adventure Mode once outside a radius.
    # switch-2-adventure-mode:
    #   everywhere: false
    #   only-outside-of-radius: 512.0

    # Uncomment to block container use while restricted.
    # forbid-container-use: true
```

See `config.yml` for the full, commented default configuration.

## Configuration

Main settings live in `config.yml`:

- `rules.otherwise-apply-restriction-strategies.keep-on-spawn.radius`: sandbox radius in normal worlds (Nether divides it by 8, the End measures it from real `0,0`).
- `message-cooldown-millis`: delay between repeated warning messages, in milliseconds.
- `restriction-refresh-interval-ticks`: how often restriction state is re-checked for online players.
- `telegram-recheck-cooldown-millis`: how long an unlinked Telegram lookup is cached before re-checking tg-bridge.
- `default-locale`: fallback language for messages.
- `messages.en` and `messages.ru`: localized messages.

## Dependency

This plugin has strong Bukkit dependency on `tg-bridge`.

`tg-bridge` must be installed and loaded before this plugin.

## Roadmap

- [ ] Discord verification check (`ds-linking-check`) - the condition schema
      already supports mixing it with Telegram linking and/or playtime via
      `all`/`any` once the check itself exists.
