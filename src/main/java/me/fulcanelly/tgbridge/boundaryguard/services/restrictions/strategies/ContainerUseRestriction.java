package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import java.util.Optional;

import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionEffect;

import org.bukkit.entity.Player;

/** Blocks container interaction (chests, furnaces, etc.) for a restricted player. */
public final class ContainerUseRestriction implements Restriction {

    @Override
    public boolean allowsContainerUse(Player player) {
        return false;
    }

    @Override
    public Optional<RestrictionEffect> effect() {
        return Optional.of(RestrictionEffect.USE_CONTAINERS);
    }
}
