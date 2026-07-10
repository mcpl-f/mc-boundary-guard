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

## Configuration

Main settings live in `config.yml`:

- `allowed-radius`: sandbox radius in normal worlds.
- `message-cooldown-millis`: delay between repeated warning messages, in milliseconds.
- `default-locale`: fallback language for messages.
- `messages.en` and `messages.ru`: localized messages.

## Dependency

This plugin has strong Bukkit dependency on `tg-bridge`.

`tg-bridge` must be installed and loaded before this plugin.
