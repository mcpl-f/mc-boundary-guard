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

    public Location getTeleportLocation(World world) {
        if (world.getEnvironment() != World.Environment.THE_END) {
            return world.getSpawnLocation();
        }

        int x = 0;
        int z = 0;
        int y = world.getHighestBlockYAt(x, z) + 1;
        return new Location(world, x + 0.5, y, z + 0.5);
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
