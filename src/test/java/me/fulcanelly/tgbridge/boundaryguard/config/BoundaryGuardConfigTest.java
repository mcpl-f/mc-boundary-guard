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
    void spawnRadiusFallsBackToAllowedRadiusWhenNotConfigured() {
        BoundaryGuardConfig config = configFrom("allowed-radius: 300\n");

        assertEquals(300.0, config.spawnRadius());
        assertFalse(config.spawnBoundaryEnabled());
    }

    @Test
    void keepOnSpawnAcceptsItsOwnRadiusIndependentOfAllowedRadius() {
        BoundaryGuardConfig config = configFrom("""
                allowed-radius: 300
                rules:
                  strategies:
                    keep-on-spawn:
                      radius: 120
                """);

        assertTrue(config.spawnBoundaryEnabled());
        assertEquals(120.0, config.spawnRadius());
    }

    @Test
    void adventureModeReadsEverywhereAndRadiusIndependently() {
        BoundaryGuardConfig config = configFrom("""
                allowed-radius: 300
                rules:
                  strategies:
                    switch-2-adventure-mode:
                      everywhere: true
                      radius: 50
                """);

        assertTrue(config.adventureModeEnabled());
        assertTrue(config.adventureModeEverywhere());
        assertEquals(50.0, config.adventureModeRadius());
    }

    @Test
    void telegramCheckIntervalIsClampedToAtLeastOneTick() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  telegram-check-interval-ticks: 0
                """);

        assertEquals(1L, config.telegramCheckIntervalTicks());
    }

    @Test
    void messageFallsBackFromLocaleToDefaultLocaleToEnglishToTheKeyItself() {
        BoundaryGuardConfig config = configFrom("""
                default-locale: de
                messages:
                  en:
                    blocked: "Blocked"
                  ru:
                    blocked: "Zabloklrovano"
                """);

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
                  condition:
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
