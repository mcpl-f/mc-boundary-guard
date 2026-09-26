package me.fulcanelly.tgbridge.boundaryguard.config;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;

import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.SpawnBoundaryRestriction;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Parses the actual config.yml shipped under src/main/resources (available on the
 * test classpath via the copied main resources) and checks that the parser,
 * condition factory and restriction factory behave the way that file's own
 * comments describe - so the shipped defaults and the code cannot silently drift
 * apart again.
 */
@ExtendWith(MockitoExtension.class)
class ShippedConfigTest {

    @Mock
    private TelegramLinkStatusService telegramLinkStatus;
    @Mock
    private Player player;

    private static BoundaryGuardConfig loadShippedConfig() {
        try (InputStream in = ShippedConfigTest.class.getResourceAsStream("/config.yml")) {
            assertNotNull(in, "config.yml must be on the test classpath");
            return new BoundaryGuardConfig(
                    YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void onlyKeepOnSpawnIsEnabledByDefault() {
        BoundaryGuardConfig config = loadShippedConfig();

        assertTrue(config.spawnBoundaryEnabled());
        assertEquals(2048.0, config.spawnRadius());
        assertFalse(config.adventureModeEnabled());
        assertFalse(config.containerUseForbidden());
    }

    @Test
    void restrictionFactoryBuildsExactlyTheSpawnBoundaryStrategy() {
        BoundaryGuardConfig config = loadShippedConfig();

        RestrictionFactory.Setup setup = new RestrictionFactory(config).create();
        List<Restriction> restrictions = setup.restrictions();

        assertEquals(1, restrictions.size());
        assertInstanceOf(SpawnBoundaryRestriction.class, restrictions.get(0));
    }

    @Test
    void defaultConditionRequiresTelegramLinking() {
        BoundaryGuardConfig config = loadShippedConfig();

        Condition condition = new ConditionFactory(telegramLinkStatus).fromConfig(config.conditionDefinition());

        when(telegramLinkStatus.isLinked(player)).thenReturn(false);
        assertFalse(condition.isMet(player));

        when(telegramLinkStatus.isLinked(player)).thenReturn(true);
        assertTrue(condition.isMet(player));
    }

    @Test
    void refreshAndTelegramCooldownSettingsMatchTheShippedValues() {
        BoundaryGuardConfig config = loadShippedConfig();

        assertEquals(60L, config.restrictionRefreshIntervalTicks());
        assertEquals(300L, config.telegramRecheckCooldownMillis());
    }

    @Test
    void defaultLocaleAndMessagesAreFullyPopulatedForBothShippedLanguages() {
        BoundaryGuardConfig config = loadShippedConfig();

        assertEquals("ru", config.defaultLocale());
        for (String key : List.of("blocked", "container-blocked", "bind-button", "bind-hover",
                "spawn-button", "spawn-hover", "teleported", "already-in-area", "only-player")) {
            assertNotEquals(key, config.message("en", key), "missing en." + key);
            assertNotEquals(key, config.message("ru", key), "missing ru." + key);
        }
    }
}
