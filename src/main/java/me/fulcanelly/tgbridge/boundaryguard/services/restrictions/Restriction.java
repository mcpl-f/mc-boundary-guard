package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Applies one kind of limitation while a player's access condition is unmet.
 *
 * A strategy only needs to override the methods for actions it controls. The
 * defaults mean that it does not affect that action. For example, a spawn-boundary
 * strategy overrides {@link #allowsMovement(Player, Location)}, while Adventure Mode
 * uses {@link #refresh(Player, boolean)} to apply and restore the game mode.
 *
 * There is deliberately no cleanup/forget hook here: none of the current strategies
 * keep per-player state that must be cleared on quit or shutdown (Adventure Mode
 * restores via the server default game mode, not a saved snapshot). A strategy that
 * does need such state should expose its own cleanup contract instead of adding an
 * empty hook that every other strategy would have to ignore.
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
     * The {@link RestrictionEffect} this strategy represents, if it has one to
     * report - used by {@code RestrictionService#activeEffects} so a reminder
     * that isn't tied to one specific action (e.g. an idle-player check) can
     * name everything currently active, not just one arbitrarily chosen effect.
     */
    default Optional<RestrictionEffect> effect() {
        return Optional.empty();
    }
}
