# Project Map

## Purpose

Spigot plugin restricting unverified players through configurable conditions and strategies. Requires `tg-bridge`. Java 17; Spigot API 1.16.4.

## Source Navigation

- `src/main/java/.../BoundaryGuardPlugin.java`: plugin entry point.
- `BoundaryGuardBootstrap.java`: build services, listeners, commands, and jobs.
- `BoundaryGuardRuntime.java`: scheduled task handles and shutdown cleanup.
- `config/BoundaryGuardConfig.java`: typed adapter for Bukkit YAML.
- `services/conditions/`: condition parsing/evaluation; nested `all`/`any`; `ReasonExpr` describes unmet requirements.
- `services/restrictions/`: restriction coordination and strategies.
- `services/messages/minecraft/`: localized component composition, colors, click/hover actions, cooldown.
- `listeners/`: thin Bukkit event adapters; command adapters live in `listeners/commands/`.
- `jobs/`: periodic restriction refresh and player reminders.
- `domain/BoundaryArea.java`: spawn-area geometry and teleport target.
- `integrations/tgbridge/`: Telegram link-status integration (hard dependency).
- `integrations/discordsrv/`: Discord link-status integration (soft dependency).
- `src/main/resources/config.yml`: shipped configuration (settings only).
- `src/main/resources/lang/`: shipped message translations, one file per locale (`en.yml`, `ru.yml`).

Keep condition logic independent of message/config presentation. Keep listeners thin. Do not add abstractions without clear ownership.

Use `@RequiredArgsConstructor` (Lombok) for any constructor that only assigns its parameters to `final` fields - don't hand-write it. Write a constructor by hand only when it does something a plain assignment can't (validation, clamping, computing a derived field) - see `MinimumPlaytimeCondition` (validates `hours`) and `TelegramLinkStatusService` (clamps `negativeCacheMillis`) for that case.

## Product and Design Docs

- `README.md`: product purpose, behavior, dependency, and configuration overview. Kept in sync with `src/main/resources/config.yml` as of the `omit-restriction-when`/`otherwise-apply-restriction-strategies` rename and the DiscordSRV soft dependency - re-verify both if either changes again.
- `features/player-messaging-principles.md`: rules for actionable, localized player messages.
- `features/restriction-condition-feedback.md`: design history for conditions, restrictions, and messages. Contains superseded historical sections; use current source/config for present behavior.
- `features/testing-strategy.md`: organize tests by service and distinct feature; keep fixtures local.
- `issues/`: known unresolved investigations.

## Tests and Commands

Tests mirror production packages under `src/test/java`. Keep distinct feature tests in separate classes, e.g. `MinecraftMessageServiceTest` for composition/cooldown and `MinecraftMessageActionComponentsTest` for click/hover component metadata.

```sh
mvn test
mvn -Dtest=MinecraftMessageActionComponentsTest test
mvn package
```

For config-to-component tests, hardcode the smallest relevant YAML fixture when that makes expected values explicit. Use `ShippedConfigTest` when verifying the distributed `config.yml` itself.
