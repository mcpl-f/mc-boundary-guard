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
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.AdventureModeRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.SpawnBoundaryRestriction;

import org.bukkit.Location;
import org.bukkit.World;
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
import static org.mockito.Mockito.mock;
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

    // The shipped config.yml ships with exactly one of keep-on-spawn /
    // switch-2-adventure-mode active at a time (both are valid defaults to
    // demonstrate) - this checks internal consistency (exactly one strategy,
    // matching whichever flag is actually set) rather than hardcoding which.
    @Test
    void exactlyOneMovementStrategyIsEnabledAndMatchesTheShippedFlags() {
        BoundaryGuardConfig config = loadShippedConfig();

        assertFalse(config.containerUseForbidden());
        assertEquals(2048.0, config.spawnRadius());
        assertTrue(
                config.spawnBoundaryEnabled() ^ config.adventureModeEnabled(),
                "expected exactly one of keep-on-spawn / switch-2-adventure-mode enabled");

        RestrictionFactory.Setup setup = new RestrictionFactory(config).create();
        List<Restriction> restrictions = setup.restrictions();

        assertEquals(1, restrictions.size());
        if (config.spawnBoundaryEnabled()) {
            assertInstanceOf(SpawnBoundaryRestriction.class, restrictions.get(0));
        } else {
            assertInstanceOf(AdventureModeRestriction.class, restrictions.get(0));
        }
    }

    @Test
    void defaultConditionRequiresTelegramLinkingUnlessAlreadyInsideTheSpawnArea() {
        BoundaryGuardConfig config = loadShippedConfig();

        Condition condition = new ConditionFactory(telegramLinkStatus).fromConfig(config.conditionDefinition());

        World world = mock(World.class);
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));

        when(telegramLinkStatus.isLinked(player)).thenReturn(false);
        when(player.getLocation()).thenReturn(new Location(world, 5000, 64, 0)); // outside 2048
        assertFalse(condition.isMet(player));

        when(player.getLocation()).thenReturn(new Location(world, 100, 64, 0)); // inside 2048
        assertTrue(condition.isMet(player));

        when(telegramLinkStatus.isLinked(player)).thenReturn(true);
        assertTrue(condition.isMet(player));
    }

    @Test
    void refreshAndTelegramCooldownSettingsMatchTheShippedValues() {
        BoundaryGuardConfig config = loadShippedConfig();

        assertEquals(60L, config.restrictionRefreshIntervalTicks());
        assertEquals(300L, config.telegramRecheckCooldownMillis());
        assertEquals(80L, config.conditionReminderIntervalTicks());
        assertTrue(config.conditionReminderEnabled());
    }

    @Test
    void defaultLocaleAndMessagesAreFullyPopulatedForBothShippedLanguages() {
        BoundaryGuardConfig config = loadShippedConfig();

        assertEquals("ru", config.defaultLocale());
        for (String key : List.of(
                "join.and", "join.or", "join.before", "join.so-that",
                "reasons.tg-linking.label", "reasons.tg-linking.command", "reasons.tg-linking.hover",
                "reasons.playtime.label",
                "reasons.return-to-spawn.label", "reasons.return-to-spawn.command", "reasons.return-to-spawn.hover",
                "restrictions.leave-spawn.gerund", "restrictions.leave-spawn.infinitive",
                "restrictions.use-containers.gerund", "restrictions.use-containers.infinitive",
                "restrictions.interact-freely.gerund", "restrictions.interact-freely.infinitive",
                "tgspawn-command.teleported", "tgspawn-command.already-in-area", "tgspawn-command.only-player")) {
            assertNotEquals(key, config.message("en", key), "missing en." + key);
            assertNotEquals(key, config.message("ru", key), "missing ru." + key);
        }
    }
}
