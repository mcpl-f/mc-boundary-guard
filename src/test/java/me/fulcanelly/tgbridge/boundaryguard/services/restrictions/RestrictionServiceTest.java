package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import java.util.List;

import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks.InSpawnRadiusCondition;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.ContainerUseRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.SpawnBoundaryRestriction;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * With a location-dependent {@link Condition} (in-spawn-radius), the player's
 * CURRENT position can satisfy it right up until the one move that crosses out
 * of the area. {@link RestrictionService#allowsMovement} must gate on whether the
 * DESTINATION satisfies the condition, not the current position - otherwise that
 * exact crossing move slips through unchecked.
 */
@ExtendWith(MockitoExtension.class)
class RestrictionServiceTest {

    @Mock
    private Player player;
    @Mock
    private World world;

    @Test
    void blocksTheExactMoveThatCrossesOutOfTheAreaEvenThoughCurrentPositionIsStillInside() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));

        BoundaryArea area = new BoundaryArea(100.0);
        Condition condition = new InSpawnRadiusCondition(area);
        RestrictionService restrictions =
                new RestrictionService(condition, List.of(new SpawnBoundaryRestriction(area)));

        when(player.getLocation()).thenReturn(new Location(world, 90, 64, 0)); // still inside

        assertFalse(restrictions.allowsMovement(player, new Location(world, 110, 64, 0)));
    }

    @Test
    void allowsMovementThatStaysInsideTheArea() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));

        BoundaryArea area = new BoundaryArea(100.0);
        Condition condition = new InSpawnRadiusCondition(area);
        RestrictionService restrictions =
                new RestrictionService(condition, List.of(new SpawnBoundaryRestriction(area)));

        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 0));

        assertTrue(restrictions.allowsMovement(player, new Location(world, 20, 64, 0)));
    }

    @Test
    void activeEffectsListsOneEffectPerStrategyThatReportsOne() {
        Condition condition = new InSpawnRadiusCondition(new BoundaryArea(100.0));
        RestrictionService restrictions = new RestrictionService(condition, List.of(
                new SpawnBoundaryRestriction(new BoundaryArea(100.0)),
                new ContainerUseRestriction()));

        assertEquals(
                List.of(RestrictionEffect.LEAVE_SPAWN, RestrictionEffect.USE_CONTAINERS),
                restrictions.activeEffects());
    }
}
