# tg-bridge-boundary-guard

Small Minecraft plugin for servers that use `tg-bridge`.

## Purpose

This plugin reduces damage from griefers and lazy anonymous players.

Unverified players stay inside a configurable sandbox area near spawn. Inside this area they can move, build, break, test, and do whatever your server rules allow.

To leave sandbox and access the wider world, player must verify through Telegram by binding Minecraft account to Telegram account. This makes player less anonymous and raises cost of griefing.

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
