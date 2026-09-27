package me.fulcanelly.tgbridge.boundaryguard.config;

import java.io.StringReader;
import java.util.List;
import java.util.Map;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BoundaryGuardConfigTest {

    private static BoundaryGuardConfig configFrom(String yaml) {
        return new BoundaryGuardConfig(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    @Test
    void spawnRadiusUsesItsOwnDefaultWhenNotConfigured() {
        BoundaryGuardConfig config = configFrom("message-cooldown-millis: 500\n");

        assertEquals(256.0, config.spawnRadius());
        assertFalse(config.spawnBoundaryEnabled());
    }

    @Test
    void keepOnSpawnIsASimpleBooleanToggle() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  otherwise-apply-restriction-strategies:
                    keep-on-spawn: true
                """);

        assertTrue(config.spawnBoundaryEnabled());
    }

    @Test
    void spawnRadiusIsReadFromTheInSpawnRadiusConditionLeaf() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  omit-restriction-when:
                    any:
                      - in-spawn-radius: 120
                """);

        assertEquals(120.0, config.spawnRadius());
    }

    @Test
    void spawnRadiusIsFoundNoMatterHowDeeplyNestedInAllOrAny() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  omit-restriction-when:
                    all:
                      - tg-linking-check: true
                      - any:
                          - in-spawn-radius: 777
                """);

        assertEquals(777.0, config.spawnRadius());
    }

    @Test
    void adventureModeIsASimpleBooleanToggle() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  otherwise-apply-restriction-strategies:
                    switch-2-adventure-mode: true
                """);

        assertTrue(config.adventureModeEnabled());
    }

    @Test
    void adventureModeDefaultsToDisabled() {
        BoundaryGuardConfig config = configFrom("rules:\n  otherwise-apply-restriction-strategies: {}\n");

        assertFalse(config.adventureModeEnabled());
    }

    @Test
    void restrictionRefreshIntervalIsClampedToAtLeastOneTick() {
        BoundaryGuardConfig config = configFrom("restriction-refresh-interval-ticks: 0\n");

        assertEquals(1L, config.restrictionRefreshIntervalTicks());
    }

    @Test
    void telegramRecheckCooldownIsClampedToAtLeastOneMillisAndIsIndependentFromTheRefreshInterval() {
        BoundaryGuardConfig config = configFrom("""
                restriction-refresh-interval-ticks: 100
                telegram-recheck-cooldown-millis: 0
                """);

        assertEquals(100L, config.restrictionRefreshIntervalTicks());
        assertEquals(1L, config.telegramRecheckCooldownMillis());
    }

    @Test
    void conditionReminderIntervalConvertsSecondsToTicks() {
        BoundaryGuardConfig config = configFrom("""
                condition-reminder:
                  interval-seconds: 3
                """);

        assertEquals(60L, config.conditionReminderIntervalTicks());
    }

    @Test
    void conditionReminderIntervalIsClampedToAtLeastOneTick() {
        BoundaryGuardConfig config = configFrom("""
                condition-reminder:
                  interval-seconds: 0
                """);

        assertEquals(1L, config.conditionReminderIntervalTicks());
    }

    @Test
    void conditionReminderIsEnabledByDefault() {
        BoundaryGuardConfig config = configFrom("");

        assertTrue(config.conditionReminderEnabled());
    }

    @Test
    void conditionReminderCanBeDisabled() {
        BoundaryGuardConfig config = configFrom("""
                condition-reminder:
                  enabled: false
                """);

        assertFalse(config.conditionReminderEnabled());
    }

    @Test
    void messageFallsBackFromLocaleToDefaultLocaleToEnglishToTheKeyItself() {
        BoundaryGuardConfig config = new BoundaryGuardConfig(
                YamlConfiguration.loadConfiguration(new StringReader("default-locale: de\n")),
                Map.of(
                        "en", YamlConfiguration.loadConfiguration(new StringReader("blocked: \"Blocked\"\n")),
                        "ru", YamlConfiguration.loadConfiguration(new StringReader("blocked: \"Zabloklrovano\"\n"))));

        assertEquals("Zabloklrovano", config.message("ru", "blocked"));
        // "fr" and the default-locale "de" both miss a translation, so this falls to "en".
        assertEquals("Blocked", config.message("fr", "blocked"));
        // Missing everywhere, including "en" -> falls back to the key itself.
        assertEquals("missing-key", config.message("fr", "missing-key"));
    }

    @Test
    void conditionDefinitionNormalizesConfigurationSectionsIntoPlainMapsAndLists() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  omit-restriction-when:
                    all:
                      - tg-linking-check
                      - time-played-limit:
                          hours: 2.5
                """);

        Object definition = config.conditionDefinition();
        assertInstanceOf(Map.class, definition);

        Map<?, ?> root = (Map<?, ?>) definition;
        assertInstanceOf(List.class, root.get("all"));

        List<?> children = (List<?>) root.get("all");
        assertEquals("tg-linking-check", children.get(0));
        assertInstanceOf(Map.class, children.get(1));
    }
}
