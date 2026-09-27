package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionEffect;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/** Confines a restricted player's movement to the configured {@link BoundaryArea}. */
@RequiredArgsConstructor
public final class SpawnBoundaryRestriction implements Restriction {

    private final BoundaryArea boundaryArea;

    @Override
    public boolean allowsMovement(Player player, Location destination) {
        return boundaryArea.contains(destination);
    }

    @Override
    public Optional<RestrictionEffect> effect() {
        return Optional.of(RestrictionEffect.LEAVE_SPAWN);
    }
}
