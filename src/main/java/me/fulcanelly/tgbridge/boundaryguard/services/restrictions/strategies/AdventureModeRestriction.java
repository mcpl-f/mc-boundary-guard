package me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies;

import java.util.Optional;

import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionEffect;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;

/**
 * Forces Adventure Mode on a restricted player.
 *
 * Purely reactive to the {@code restricted} flag it's handed - it has no location
 * logic of its own. Whether "restricted" already accounts for location (e.g. an
 * {@code in-spawn-radius} condition) is {@code rules.omit-restriction-when}'s call, not this
 * strategy's; it used to duplicate that check locally (its own boundary area plus
 * an {@code everywhere} flag), which meant the same spatial concept was configured
 * in two places that could drift out of sync.
 *
 * Restoring uses the server's configured default game mode rather than a saved
 * per-player snapshot. A snapshot lives only in memory, so a server restart would
 * lose it while the player's Adventure Mode persists, leaving the plugin unable to
 * restore a mode it no longer remembers. This intentionally does not preserve an
 * individual player's own non-default mode.
 */
public final class AdventureModeRestriction implements Restriction {

    @Override
    public void refresh(Player player, boolean restricted) {
        if (!restricted) {
            restore(player);
            return;
        }

        if (player.getGameMode() != GameMode.ADVENTURE) {
            player.setGameMode(GameMode.ADVENTURE);
        }
    }

    private void restore(Player player) {
        if (player.getGameMode() == GameMode.ADVENTURE) {
            player.setGameMode(player.getServer().getDefaultGameMode());
        }
    }

    @Override
    public Optional<RestrictionEffect> effect() {
        return Optional.of(RestrictionEffect.INTERACT_FREELY);
    }
}
