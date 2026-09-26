package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.List;
import java.util.Optional;

import org.bukkit.entity.Player;

/**
 * Checks one rule that determines whether a player has access to unrestricted play.
 *
 * Implementations answer a single question about the player, such as whether their
 * Telegram account is linked or whether they have played long enough. Conditions
 * can be combined with {@code all}/{@code any} by {@link ConditionApplier}.
 */
public interface Condition {

    /**
     * Returns whether this player satisfies the condition.
     *
     * A {@code true} result means this rule is satisfied; {@code false} means it
     * still contributes to the player's restrictions. Implementations should only
     * check their own rule and leave restriction changes to {@code RestrictionService}.
     */
    boolean isMet(Player player);

    /**
     * The {@link ConditionReason} this leaf condition represents, if it has one to
     * report. Empty for a condition with nothing useful to say on its own -
     * {@link ConditionApplier} is the only current implementation that needs to
     * override {@link #unmetReasons} directly instead, since it must recurse into
     * its children rather than report a single reason for itself.
     */
    default Optional<ConditionReason> reason() {
        return Optional.empty();
    }

    /**
     * Collects the {@link #reason()} of every currently-unmet condition in this
     * tree, so a combined "here's what you still need" message can be built. The
     * default (used by every leaf condition) just checks {@link #isMet}; only a
     * composite needs to override this to walk its children instead.
     */
    default List<ConditionReason> unmetReasons(Player player) {
        if (isMet(player)) {
            return List.of();
        }
        return reason().map(List::of).orElseGet(List::of);
    }
}
