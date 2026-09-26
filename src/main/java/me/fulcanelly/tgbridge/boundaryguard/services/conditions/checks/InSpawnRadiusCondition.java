package me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Met while the player is within the configured spawn area - lets an
 * otherwise-unverified player be treated as unrestricted as long as they stay
 * put, without requiring Telegram/playtime verification at all.
 *
 * Unlike every other {@link Condition}, this one is location-dependent, so it
 * overrides {@link #isMet(Player, Location)} directly instead of the
 * player-only {@link #isMet(Player)} - a movement check needs to ask about the
 * destination, not the player's current spot.
 */
@RequiredArgsConstructor
public final class InSpawnRadiusCondition implements Condition {

    private final BoundaryArea boundaryArea;

    @Override
    public boolean isMet(Player player) {
        return boundaryArea.contains(player.getLocation());
    }

    @Override
    public boolean isMet(Player player, Location location) {
        return boundaryArea.contains(location);
    }
}
