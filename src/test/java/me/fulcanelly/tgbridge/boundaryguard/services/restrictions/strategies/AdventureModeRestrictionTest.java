package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;

import org.bukkit.GameMode;
import org.bukkit.Server;
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
}
