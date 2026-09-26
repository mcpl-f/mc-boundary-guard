package me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;

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
 *
 * Its {@link #reason()} is {@link ConditionReason#RETURN_TO_SPAWN} - unlike
 * Telegram linking or playtime, satisfying it isn't durable (stepping back out
 * the moment you move means the condition is unmet again the same way), but it
 * genuinely is one of the alternatives {@code rules.condition} accepts, so a
 * player blocked from something else (containers, an idle reminder) benefits
 * from being told it's an option. The one place that's NOT true is the message
 * describing the {@code leave-spawn} restriction itself - "return to spawn" as
 * the offered fix for "you can't leave spawn" is circular, since it names the
 * exact thing the player is currently failing to do. The messages layer
 * (see {@code MinecraftMessageService#reasonMessage}) is what excludes this
 * reason specifically there; this condition doesn't need to know about that -
 * it just reports the reason like any other.
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

    @Override
    public Optional<ConditionReason> reason() {
        return Optional.of(ConditionReason.RETURN_TO_SPAWN);
    }
}
