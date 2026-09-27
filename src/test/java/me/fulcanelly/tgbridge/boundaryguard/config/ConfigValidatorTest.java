package me.fulcanelly.tgbridge.boundaryguard.config;

import java.io.StringReader;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigValidatorTest {

    private static BoundaryGuardConfig configFrom(String yaml) {
        return new BoundaryGuardConfig(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    @Test
    void acceptsAValidConfig() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  omit-restriction-when:
                    tg-linking-check: true
                  otherwise-apply-restriction-strategies:
                    keep-on-spawn: true
                """);

        assertDoesNotThrow(() -> new ConfigValidator(config).validate());
    }

    @Test
    void rejectsAnUnknownConditionLeaf() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  omit-restriction-when:
                    not-a-real-check: true
                  otherwise-apply-restriction-strategies:
                    keep-on-spawn: true
                """);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> new ConfigValidator(config).validate());
        assertTrue(exception.getMessage().contains("rules.omit-restriction-when"));
        assertTrue(exception.getMessage().contains("not-a-real-check"));
    }

    @Test
    void rejectsNoStrategyEnabled() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  omit-restriction-when:
                    tg-linking-check: true
                """);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> new ConfigValidator(config).validate());
        assertTrue(exception.getMessage().contains("rules.otherwise-apply-restriction-strategies"));
    }

    // Both problems are reported together in one exception, rather than
    // stopping at whichever one the validator happens to check first - an
    // admin fixing a broken config.yml shouldn't have to restart the server
    // repeatedly just to find the next mistake.
    @Test
    void reportsBothAConditionProblemAndARestrictionProblemTogether() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  omit-restriction-when:
                    not-a-real-check: true
                """);

        IllegalStateException exception =
                assertThrows(IllegalStateException.class, () -> new ConfigValidator(config).validate());
        assertTrue(exception.getMessage().contains("rules.omit-restriction-when"));
        assertTrue(exception.getMessage().contains("rules.otherwise-apply-restriction-strategies"));
    }
}
