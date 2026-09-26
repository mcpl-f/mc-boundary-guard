package me.fulcanelly.tgbridge.boundaryguard.domain;

import org.bukkit.Location;
import org.bukkit.World;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoundaryAreaTest {

    @Mock
    private World world;

    @Test
    void normalWorldAnchorsAtSpawnWithTheFullRadius() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 100, 64, 100));

        BoundaryArea area = new BoundaryArea(50.0);

        assertTrue(area.contains(new Location(world, 130, 64, 100)));
        assertFalse(area.contains(new Location(world, 200, 64, 100)));
    }

    @Test
    void netherAnchorsAtSpawnButDividesTheConfiguredRadiusByEight() {
        when(world.getEnvironment()).thenReturn(World.Environment.NETHER);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));

        BoundaryArea area = new BoundaryArea(80.0); // effective radius: 10

        assertTrue(area.contains(new Location(world, 9, 64, 0)));
        assertFalse(area.contains(new Location(world, 11, 64, 0)));
    }

    @Test
    void endAnchorsAtRealZeroZeroWithTheFullRadius() {
        when(world.getEnvironment()).thenReturn(World.Environment.THE_END);
        // getSpawnLocation() is deliberately left unstubbed: the End anchor must
        // not consult it at all, unlike every other environment.

        BoundaryArea area = new BoundaryArea(50.0);

        assertTrue(area.contains(new Location(world, 30, 64, 0)));
        assertFalse(area.contains(new Location(world, 1000, 64, 1000)));
    }

    @Test
    void teleportLocationUsesTheWorldsSpawnLocation() {
        Location spawn = new Location(world, 5, 70, 5);
        when(world.getSpawnLocation()).thenReturn(spawn);

        BoundaryArea area = new BoundaryArea(50.0);

        assertEquals(spawn, area.getTeleportLocation(world));
    }
}
