package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Applies one kind of limitation while a player's access condition is unmet.
 *
 * A strategy only needs to override the methods for actions or state it controls.
 * The defaults mean that it does not affect that action and has no temporary
 * state to update or clear. For example, a spawn-boundary strategy overrides
 * {@link #allowsMovement(Player, Location)}, while Adventure Mode uses
 * {@link #refresh(Player, boolean)} and {@link #forget(Player)}.
 */
public interface Restriction {

    /**
     * Updates player state when the access rule is reevaluated.
     *
     * Use this for stateful restrictions such as switching a restricted player to
     * Adventure Mode. {@code restricted} is true while the configured condition is
     * unmet. This method is called by {@code RestrictionService} during refreshes.
     */
    default void refresh(Player player, boolean restricted) {
    }

    /**
     * Decides whether a movement destination is allowed while the player is restricted.
     *
     * Return false to make the movement listener cancel this move. Override this
     * for location-based rules such as keeping players inside a spawn boundary.
     * Strategies unrelated to movement can keep the default, which allows it.
     */
    default boolean allowsMovement(Player player, Location destination) {
        return true;
    }

    /**
     * Decides whether the player may use a container while restricted.
     *
     * Return false to make the inventory listener cancel the interaction. Override
     * this for container-use rules; unrelated strategies keep the default and allow it.
     */
    default boolean allowsContainerUse(Player player) {
        return true;
    }

    /**
     * Clears per-player state when the player quits or the plugin shuts down.
     *
     * Stateful strategies should undo temporary changes here, such as restoring a
     * saved game mode. Stateless strategies do not need to override this method.
     */
    default void forget(Player player) {
    }
}
