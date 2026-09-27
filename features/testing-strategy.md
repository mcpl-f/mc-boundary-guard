# Test Organization Strategy

Tests should be organized around the behavior they verify, not accumulated in one large test class per package.

## Placement

- Mirror the production package structure under `src/test/java`, keeping tests near the service or class they exercise.
- Keep a focused test class for each distinct feature of a service. For example, `MinecraftMessageServiceTest` covers message composition and cooldown behavior, while `MinecraftMessageActionComponentsTest` covers clickable labels, hover payloads, and component-tree structure.
- Use names that describe the feature under test; avoid generic catch-all classes as a feature grows.

## Test Scope

- Assert observable behavior and public outputs. For chat components, verify visible text, `clickEvent`, `hoverEvent`, and relevant `extra` children rather than private helper methods.
- When testing how configuration produces a feature's output, hardcode the smallest relevant configuration in that feature's test. This keeps the behavior reproducible and makes the values under test explicit.
- Test the shipped resource separately when the requirement is specifically that the distributed configuration contains valid keys or defaults; do not make every unit test depend on the resource file.
- Keep each test class's fixtures and helpers local to the feature that uses them. Move a distinct set of tests and its fixtures to a dedicated class instead of continuing to grow a broad service test.

## Validation

Run the narrow feature test class while iterating, then run its neighboring service tests after moving or changing shared behavior. Use the full Maven test suite when changes cross service boundaries or affect shared configuration contracts.
