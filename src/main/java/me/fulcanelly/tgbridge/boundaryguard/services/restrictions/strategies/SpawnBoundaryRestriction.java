package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class SpawnBoundaryRestriction implements Restriction {

    private final BoundaryArea boundaryArea;

    public SpawnBoundaryRestriction(BoundaryArea boundaryArea) {
        this.boundaryArea = boundaryArea;
    }

    @Override
    public boolean allowsMovement(Player player, Location destination) {
        return boundaryArea.contains(destination);
    }
}
