package me.fulcanelly.tgbridge.boundaryguard.domain;

import lombok.RequiredArgsConstructor;

import org.bukkit.Location;
import org.bukkit.World;

/**
 * A circular area around a per-world anchor, used to decide whether a location
 * counts as inside the sandbox.
 *
 * The anchor and radius depend on the world: normal worlds use the world's spawn
 * location; the Nether also uses spawn but divides the radius by 8 to match its
 * coordinate scaling relative to the Overworld; the End uses real {@code 0,0}
 * instead of its spawn location, since the End's spawn platform is not a
 * meaningful "center" for this plugin's sandbox.
 */
@RequiredArgsConstructor
public final class BoundaryArea {

    private final double configuredRadius;

    public boolean contains(Location location) {
        Location anchor = getAnchor(location.getWorld());
        double radius = getRadius(location.getWorld());
        double dx = location.getX() - anchor.getX();
        double dz = location.getZ() - anchor.getZ();

        return dx * dx + dz * dz <= radius * radius;
    }

    // Every world, The End included, has a tracked spawn location - using it
    // uniformly avoids guessing a fixed (0,0) column that might have no solid
    // ground (e.g. terrain broken near the anchor point) and would drop the
    // player into the void.
    public Location getTeleportLocation(World world) {
        return world.getSpawnLocation();
    }

    private Location getAnchor(World world) {
        if (world.getEnvironment() == World.Environment.THE_END) {
            return new Location(world, 0, 0, 0);
        }

        return world.getSpawnLocation();
    }

    private double getRadius(World world) {
        if (world.getEnvironment() == World.Environment.NETHER) {
            return configuredRadius / 8.0;
        }

        return configuredRadius;
    }
}
