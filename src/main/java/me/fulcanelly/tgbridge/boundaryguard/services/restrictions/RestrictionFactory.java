package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import java.util.ArrayList;
import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.AdventureModeRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.ContainerUseRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.SpawnBoundaryRestriction;

/** Converts restriction settings into the area and strategies used at runtime. */
@RequiredArgsConstructor
public final class RestrictionFactory {

    private final BoundaryGuardConfig config;

    public Setup create() {
        BoundaryArea spawnArea = new BoundaryArea(config.spawnRadius());
        List<Restriction> restrictions = new ArrayList<>();

        if (config.spawnBoundaryEnabled()) {
            restrictions.add(new SpawnBoundaryRestriction(spawnArea));
        }

        if (config.adventureModeEnabled()) {
            restrictions.add(new AdventureModeRestriction(
                    new BoundaryArea(config.adventureModeOutsideRadius()),
                    config.adventureModeEverywhere()));
        }

        if (config.containerUseForbidden()) {
            restrictions.add(new ContainerUseRestriction());
        }

        if (restrictions.isEmpty()) {
            // Caught by BoundaryGuardPlugin#onEnable, which logs it (severe, console
            // only) and disables the plugin - this is always a misconfiguration, not
            // a valid "do nothing" mode.
            throw new IllegalStateException(
                    "No restriction strategies enabled under rules.strategies in config.yml "
                            + "- unverified players would not be restricted at all. Enable at least "
                            + "one strategy (keep-on-spawn, switch-2-adventure-mode, forbid-container-use).");
        }

        return new Setup(spawnArea, List.copyOf(restrictions));
    }

    public record Setup(BoundaryArea spawnArea, List<Restriction> restrictions) {
    }
}
