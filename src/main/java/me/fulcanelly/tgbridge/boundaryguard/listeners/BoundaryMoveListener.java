package me.fulcanelly.tgbridge.boundaryguard.listeners;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

@RequiredArgsConstructor
public final class BoundaryMoveListener implements Listener {

    private final RestrictionService restrictions;
    private final MinecraftMessageService messages;

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || isSameBlock(event.getFrom(), to)) {
            return;
        }

        Player player = event.getPlayer();
        if (restrictions.allowsMovement(player, to)) {
            return;
        }

        // Pins the player at `from` rather than teleporting them back into the area -
        // including for a player who became restricted while already standing
        // outside it (e.g. rules.condition was tightened after they'd wandered off).
        // That's intentional: `/tgspawn`, advertised right below, is the player's own
        // way back, not something this listener does for them.
        event.setTo(event.getFrom());
        messages.sendBoundaryBlocked(player);
    }

    private boolean isSameBlock(Location from, Location to) {
        return from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

}
