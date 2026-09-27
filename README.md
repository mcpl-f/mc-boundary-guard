# Boundary Guard

Rule-based anti-grief protection for Minecraft servers.

Define who should be restricted using playtime, location, account verification, or nested combinations of conditions — then choose exactly which restrictions to apply.

From simple spawn boundaries to multi-condition trust systems, Boundary Guard keeps protection configurable without forcing a specific verification provider or region setup.

## Purpose

Boundary Guard reduces griefing and abuse from new, anonymous, or otherwise untrusted players.

Instead of protecting regions manually, you define when a player should be considered trusted and which restrictions apply until then.

Players can earn unrestricted access through playtime, account verification, location-based rules, or any combination of conditions.

The goal is simple: restrict players only while necessary, then leave normal gameplay completely untouched.

## Contents

- [Quick Start](#quick-start)
- [Common Configurations](#common-configurations)
  - [1. Verify with Telegram](#1-verify-with-telegram)
  - [2. Verify with Telegram or Discord](#2-verify-with-telegram-or-discord)
  - [3. Require Playtime + Verification](#3-require-playtime--verification)
- [Restriction Strategies](#restriction-strategies)
- [Rules](#rules)
- [Spawn Area](#spawn-area)
- [Player Experience](#player-experience)
- [Advanced Rules](#advanced-rules)
  - [Different Requirements Combined](#different-requirements-combined)
  - [Verification Without a Spawn Exception](#verification-without-a-spawn-exception)
- [Configuration](#configuration)
- [Languages](#languages)
- [Dependencies](#dependencies)
  - [tg-bridge](#tg-bridge)
  - [DiscordSRV](#discordsrv)

## Quick Start

Install:

- `tg-bridge` — required
- Boundary Guard
- `DiscordSRV` — optional, only needed for Discord verification

Drop the plugin `.jar` into `plugins/`, restart the server, and choose a rule setup below.

## Common Configurations

### 1. Verify with Telegram

Players can play normally within 2048 blocks of spawn.

To go further, they must link their Minecraft account to Telegram.

```yml
rules:
  omit-restriction-when:
    any:
      - tg-linking-check: true
      - in-spawn-radius: 2048

  otherwise-apply-restriction-strategies:
    keep-on-spawn: true
```

### 2. Verify with Telegram or Discord

Either linked account is enough to leave the spawn area.

```yml
rules:
  omit-restriction-when:
    any:
      - tg-linking-check: true
      - ds-linking-check: true
      - in-spawn-radius: 2048

  otherwise-apply-restriction-strategies:
    keep-on-spawn: true
```

`DiscordSRV` must be installed when `ds-linking-check` is used.

### 3. Require playtime + verification

Players must spend at least 1 hour on the server and link either Telegram or Discord before restrictions are removed.

```yml
rules:
  omit-restriction-when:
    any:
      - in-spawn-radius: 2048
      - all:
          - time-played-limit:
              hours: 1.0
          - any:
              - tg-linking-check: true
              - ds-linking-check: true

  otherwise-apply-restriction-strategies:
    keep-on-spawn: true
```

For most servers, one of these three configurations is enough.

## Restriction Strategies

Restrictions are configured under:

```yml
rules:
  otherwise-apply-restriction-strategies:
```

Available strategies:

- `keep-on-spawn` — prevents restricted players from leaving the spawn area.
- `switch-2-adventure-mode` — switches restricted players to Adventure Mode while outside the spawn area.
- `forbid-container-use` — prevents restricted players from using containers.

Strategies can be combined:

```yml
rules:
  otherwise-apply-restriction-strategies:
    keep-on-spawn: true
    switch-2-adventure-mode: true
    forbid-container-use: true
```

For example, combining `keep-on-spawn` with `switch-2-adventure-mode` gives you a hard boundary plus a second line of protection if a restricted player somehow ends up outside it.

## Rules

`rules.omit-restriction-when` defines when a player is considered unrestricted.

Available conditions:

- `tg-linking-check` — Minecraft account is linked to Telegram through `tg-bridge`.
- `ds-linking-check` — Minecraft account is linked to Discord through `DiscordSRV`.
- `time-played-limit` — player has reached the configured total playtime.
- `in-spawn-radius` — player is currently inside the configured spawn area.

Conditions can be combined using:

- `any` — at least one condition must be satisfied.
- `all` — every condition must be satisfied.

They can be nested to any depth.

For example:

```yml
all:
  - time-played-limit:
      hours: 2.5
  - any:
      - tg-linking-check: true
      - ds-linking-check: true
```

means:

> The player must have at least 2.5 hours of playtime AND have either Telegram OR Discord linked.

## Spawn Area

`in-spawn-radius` is the single source of truth for the spawn area.

```yml
- in-spawn-radius: 2048
```

The radius is interpreted as:

- Normal worlds — distance from the world's spawn.
- Nether — configured distance divided by 8.
- End — distance from real `0,0`.

All enabled restriction strategies use this same radius. There is no separate radius to configure for each strategy.

## Player Experience

When a player hits a restriction, Boundary Guard shows actions that are actually available on the server.

Depending on your configuration, the player may be offered:

- `/tg account register` — link Telegram.
- `/discord link` — link Discord.
- `/tgspawn` — teleport back to the allowed spawn area.

`/tgspawn` is always available as a way back for players who do not want to verify yet.

Messages are rate-limited to avoid spam.

## Advanced Rules

Because `any` and `all` are nestable, more complex policies are possible.

### Different requirements combined

```yml
rules:
  omit-restriction-when:
    any:
      - in-spawn-radius: 1024

      - all:
          - time-played-limit:
              hours: 2.5
          - any:
              - tg-linking-check: true
              - ds-linking-check: true

  otherwise-apply-restriction-strategies:
    keep-on-spawn: true
    switch-2-adventure-mode: true
```

This means:

- everyone can stay within 1024 blocks of spawn;
- outside that area, the player needs at least 2.5 hours of playtime;
- and either Telegram or Discord must be linked.

### Verification without a spawn exception

```yml
rules:
  omit-restriction-when:
    all:
      - time-played-limit:
          hours: 2.5
      - tg-linking-check: true

  otherwise-apply-restriction-strategies:
    switch-2-adventure-mode: true
    forbid-container-use: true
```

Here, being near spawn does not make the player unrestricted.

Until both conditions are satisfied, the configured restriction strategies continue to apply.

## Configuration

## Configuration

Additional settings live in `config.yml`:

| Setting | Description |
|---|---|
| `message-cooldown-millis` | Delay between repeated warning messages. |
| `restriction-refresh-interval-ticks` | How often online player restriction states are refreshed. |
| `telegram-recheck-cooldown-millis` | How long an unlinked Telegram result is cached before checking `tg-bridge` again. |
| `condition-reminder.enabled` | Enables periodic verification reminders. |
| `condition-reminder.interval-seconds` | Interval between reminders. |
| `default-locale` | Fallback message language. |

See the shipped `config.yml` for the full commented configuration.

## Languages

Translations live under:

```text
lang/<locale>.yml
```

Included by default:

```text
lang/en.yml
lang/ru.yml
```

Additional `lang/*.yml` files placed in the plugin data directory are discovered automatically.

## Dependencies

### tg-bridge

`tg-bridge` is required and must be loaded before Boundary Guard.

### DiscordSRV

`DiscordSRV` is optional.

It is only required if `ds-linking-check` appears anywhere inside `rules.omit-restriction-when`.

If `ds-linking-check` is configured without DiscordSRV installed, Boundary Guard fails during startup with a clear error.
