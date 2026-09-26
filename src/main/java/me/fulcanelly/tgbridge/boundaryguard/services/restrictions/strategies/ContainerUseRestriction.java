package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;

import org.bukkit.entity.Player;

/** Blocks container interaction (chests, furnaces, etc.) for a restricted player. */
public final class ContainerUseRestriction implements Restriction {

    @Override
    public boolean allowsContainerUse(Player player) {
        return false;
    }
}
