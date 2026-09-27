package me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks;

import java.util.Optional;

import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;

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

@ExtendWith(MockitoExtension.class)
class InSpawnRadiusConditionTest {

    @Mock
    private Player player;
    @Mock
    private World world;

    @Test
    void isMetWhilePlayerIsInsideTheRadius() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 0));

        InSpawnRadiusCondition condition = new InSpawnRadiusCondition(new BoundaryArea(100.0));

        assertTrue(condition.isMet(player));
    }

    @Test
    void isUnmetOnceThePlayerLeavesTheRadius() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getLocation()).thenReturn(new Location(world, 1000, 64, 0));

        InSpawnRadiusCondition condition = new InSpawnRadiusCondition(new BoundaryArea(100.0));

        assertFalse(condition.isMet(player));
    }

    // Not durable the way Telegram linking or playtime are (stepping back out
    // re-triggers the same block), but still a real rules.omit-restriction-when
    // alternative - see MinecraftMessageService#reasonMessage for where that
    // distinction actually gets enforced (excluded only from the
    // leave-spawn-restriction message, shown everywhere else).
    @Test
    void reasonIsReturnToSpawn() {
        InSpawnRadiusCondition condition = new InSpawnRadiusCondition(new BoundaryArea(100.0));

        assertEquals(Optional.of(ConditionReason.RETURN_TO_SPAWN), condition.reason());
    }

    @Test
    void locationOverloadChecksTheGivenLocationNotThePlayersCurrentOne() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        // Player's own location is deliberately left unstubbed: this overload must
        // not consult it at all - a movement check needs the destination's answer.
        Location destination = new Location(world, 10, 64, 0);

        InSpawnRadiusCondition condition = new InSpawnRadiusCondition(new BoundaryArea(100.0));

        assertTrue(condition.isMet(player, destination));
    }
}
