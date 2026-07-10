package me.fulcanelly.tgbridge.boundaryguard;

import org.bukkit.Location;
import org.bukkit.World;

final class BoundaryArea {

    private final double configuredRadius;

    BoundaryArea(double configuredRadius) {
        this.configuredRadius = configuredRadius;
    }

    boolean contains(Location location) {
        Location anchor = getAnchor(location.getWorld());
        double radius = getRadius(location.getWorld());
        double dx = location.getX() - anchor.getX();
        double dz = location.getZ() - anchor.getZ();

        return dx * dx + dz * dz <= radius * radius;
    }

    Location getTeleportLocation(World world) {
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
