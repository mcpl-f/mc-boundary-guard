package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Checks one rule that determines whether a player has access to unrestricted play.
 *
 * Implementations answer a single question about the player, such as whether their
 * Telegram account is linked, whether they have played long enough, or whether a
 * given location counts as inside the spawn area. Conditions can be combined with
 * {@code all}/{@code any} by {@link ConditionApplier}.
 */
public interface Condition {

    /**
     * Returns whether this player satisfies the condition at their current location.
     *
     * A {@code true} result means this rule is satisfied; {@code false} means it
     * still contributes to the player's restrictions. Implementations should only
     * check their own rule and leave restriction changes to {@code RestrictionService}.
     */
    boolean isMet(Player player);

    /**
     * Returns whether this player would satisfy the condition if they were standing
     * at {@code location} instead of wherever they currently are.
     *
     * Only a location-dependent condition (e.g. "inside the spawn area") needs to
     * override this; the default delegates to {@link #isMet(Player)}, which is
     * correct for every condition that doesn't care about location at all (Telegram
     * linking, playtime). This exists so a movement check can ask "would the
     * destination be fine", not just "is the player's current spot fine" - those
     * are different questions, and answering the second one for a movement check
     * would let a player step past the boundary on the one move that actually
     * crosses it.
     */
    default boolean isMet(Player player, Location location) {
        return isMet(player);
    }

    /**
     * The {@link ConditionReason} this leaf condition represents, if it has one to
     * report. Empty for a condition with nothing useful to say on its own -
     * {@link ConditionApplier} is the only current implementation that needs to
     * override {@link #unmetReason} directly instead, since it must recurse into
     * its children rather than report a single reason for itself.
     */
    default Optional<ConditionReason> reason() {
        return Optional.empty();
    }

    /**
     * Builds a {@link ReasonExpr} describing why this condition (and, for a
     * composite, its children) is currently unmet, preserving the {@code all}/
     * {@code any} structure so a message can join multiple reasons with "and"/
     * "or" correctly. The default (used by every leaf condition) just checks
     * {@link #isMet}; only {@link ConditionApplier} needs to override this to
     * walk its children instead.
     */
    default Optional<ReasonExpr> unmetReason(Player player) {
        if (isMet(player)) {
            return Optional.empty();
        }
        return reason().map(ReasonExpr.Leaf::new);
    }
}
