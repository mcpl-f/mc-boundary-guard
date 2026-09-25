package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class AdventureModeRestriction implements Restriction {

    private final BoundaryArea boundaryArea;
    private final boolean everywhere;
    private final Map<UUID, GameMode> previousModes = new HashMap<>();

    @Override
    public void refresh(Player player, boolean restricted) {
        boolean shouldApply = restricted && (everywhere || boundaryArea.contains(player.getLocation()));
        UUID playerId = player.getUniqueId();

        if (shouldApply) {
            if (!previousModes.containsKey(playerId)) {
                previousModes.put(playerId, player.getGameMode());
            }
            if (player.getGameMode() != GameMode.ADVENTURE) {
                player.setGameMode(GameMode.ADVENTURE);
            }
        } else {
            restore(player);
        }
    }

    @Override
    public void forget(Player player) {
        restore(player);
    }

    private void restore(Player player) {
        GameMode previousMode = previousModes.remove(player.getUniqueId());
        if (previousMode != null && player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(previousMode);
        }
    }
}
