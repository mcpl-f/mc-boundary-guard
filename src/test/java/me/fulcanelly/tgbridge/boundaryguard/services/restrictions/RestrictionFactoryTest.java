package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import java.io.StringReader;
import java.util.List;

import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.AdventureModeRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.SpawnBoundaryRestriction;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RestrictionFactoryTest {

    private static BoundaryGuardConfig configFrom(String yaml) {
        return new BoundaryGuardConfig(YamlConfiguration.loadConfiguration(new StringReader(yaml)));
    }

    // README describes combining keep-on-spawn with Adventure Mode "for stricter
    // anti-grief protection" - this is the only test exercising both together.
    @Test
    void combinesKeepOnSpawnAndAdventureModeWhenBothAreEnabled() {
        BoundaryGuardConfig config = configFrom("""
                rules:
                  condition:
                    any:
                      - in-spawn-radius: 200
                  strategies:
                    keep-on-spawn: true
                    switch-2-adventure-mode: true
                """);

        RestrictionFactory.Setup setup = new RestrictionFactory(config).create();
        List<Restriction> restrictions = setup.restrictions();

        assertEquals(2, restrictions.size());
        assertInstanceOf(SpawnBoundaryRestriction.class, restrictions.get(0));
        assertInstanceOf(AdventureModeRestriction.class, restrictions.get(1));
    }

    @Test
    void throwsWhenNoStrategyIsEnabled() {
        BoundaryGuardConfig config = configFrom("rules:\n  strategies: {}\n");

        assertThrows(IllegalStateException.class, () -> new RestrictionFactory(config).create());
    }
}
