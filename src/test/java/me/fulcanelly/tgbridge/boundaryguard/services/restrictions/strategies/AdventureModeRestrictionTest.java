package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdventureModeRestrictionTest {

    @Mock
    private Player player;
    @Mock
    private Server server;
    @Mock
    private World world;

    // Regression test for the previous in-memory `previousModes` snapshot: since
    // there is no such state anymore, a brand-new instance (standing in for the
    // state a server restart would wipe) must still restore correctly.
    @Test
    void restoresToServerDefaultModeEvenFromABrandNewInstance() {
        BoundaryArea everywhereArea = new BoundaryArea(0.0);
        AdventureModeRestriction beforeRestart = new AdventureModeRestriction(everywhereArea, true);

        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);
        beforeRestart.refresh(player, true);
        verify(player).setGameMode(GameMode.ADVENTURE);

        AdventureModeRestriction afterRestart = new AdventureModeRestriction(everywhereArea, true);

        when(player.getGameMode()).thenReturn(GameMode.ADVENTURE);
        when(player.getServer()).thenReturn(server);
        when(server.getDefaultGameMode()).thenReturn(GameMode.SURVIVAL);

        afterRestart.refresh(player, false);

        verify(player).setGameMode(GameMode.SURVIVAL);
    }

    @Test
    void doesNotTouchGameModeWhenPlayerWasNeverSwitchedToAdventure() {
        BoundaryArea everywhereArea = new BoundaryArea(0.0);
        AdventureModeRestriction restriction = new AdventureModeRestriction(everywhereArea, true);

        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        restriction.refresh(player, false);

        verify(player, never()).setGameMode(any());
    }

    // config.yml documents `only-outside-of-radius` as switching to Adventure Mode
    // once the player leaves the area, not while inside it - this pins that polarity.
    @Test
    void appliesAdventureModeOnceThePlayerLeavesTheConfiguredRadius() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getLocation()).thenReturn(new Location(world, 1000, 64, 0));
        when(player.getGameMode()).thenReturn(GameMode.SURVIVAL);

        AdventureModeRestriction restriction = new AdventureModeRestriction(new BoundaryArea(100.0), false);
        restriction.refresh(player, true);

        verify(player).setGameMode(GameMode.ADVENTURE);
    }

    @Test
    void doesNotApplyAdventureModeWhileThePlayerIsStillInsideTheRadius() {
        when(world.getEnvironment()).thenReturn(World.Environment.NORMAL);
        when(world.getSpawnLocation()).thenReturn(new Location(world, 0, 64, 0));
        when(player.getLocation()).thenReturn(new Location(world, 10, 64, 0));

        AdventureModeRestriction restriction = new AdventureModeRestriction(new BoundaryArea(100.0), false);
        restriction.refresh(player, true);

        verify(player, never()).setGameMode(any());
    }
}
