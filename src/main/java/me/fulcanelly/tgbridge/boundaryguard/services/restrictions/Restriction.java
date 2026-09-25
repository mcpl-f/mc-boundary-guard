package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public interface Restriction {

    default void refresh(Player player, boolean restricted) {
    }

    default boolean allowsMovement(Player player, Location destination) {
        return true;
    }

    default boolean allowsContainerUse(Player player) {
        return true;
    }

    default void forget(Player player) {
    }
}
