package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;

import org.bukkit.Location;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class SpawnBoundaryRestriction implements Restriction {

    private final BoundaryArea boundaryArea;

    @Override
    public boolean allowsMovement(Player player, Location destination) {
        return boundaryArea.contains(destination);
    }
}
