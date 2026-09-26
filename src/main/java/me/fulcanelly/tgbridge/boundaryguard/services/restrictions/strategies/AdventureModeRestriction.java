package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Forces Adventure Mode on a restricted player once they leave the boundary area
 * (or, with {@code everywhere}, regardless of location).
 *
 * The area is left alone so a restricted player keeps full survival privileges
 * near spawn (matching this plugin's "build/break/test at spawn" purpose); Adventure
 * Mode only kicks in once they wander past it, so the wider world stays protected
 * even without a hard movement block.
 *
 * Restoring uses the server's configured default game mode rather than a saved
 * per-player snapshot. A snapshot lives only in memory, so a server restart would
 * lose it while the player's Adventure Mode persists, leaving the plugin unable to
 * restore a mode it no longer remembers. This intentionally does not preserve an
 * individual player's own non-default mode.
 */
@RequiredArgsConstructor
public final class AdventureModeRestriction implements Restriction {

    private final BoundaryArea boundaryArea;
    private final boolean everywhere;

    @Override
    public void refresh(Player player, boolean restricted) {
        boolean shouldApply = restricted && (everywhere || !boundaryArea.contains(player.getLocation()));

        if (!shouldApply) {
            restore(player);
            return;
        }

        if (player.getGameMode() != GameMode.ADVENTURE) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void restore(Player player) {
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(player.getServer().getDefaultGameMode());
        }
    }
}
