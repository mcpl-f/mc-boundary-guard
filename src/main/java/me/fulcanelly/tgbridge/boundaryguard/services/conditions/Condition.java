package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

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
}
