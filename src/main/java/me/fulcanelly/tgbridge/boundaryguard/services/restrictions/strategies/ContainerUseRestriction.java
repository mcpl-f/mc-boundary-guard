package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;

import org.bukkit.entity.Player;

public final class ContainerUseRestriction implements Restriction {

    @Override
    public boolean allowsContainerUse(Player player) {
        return false;
    }
}
