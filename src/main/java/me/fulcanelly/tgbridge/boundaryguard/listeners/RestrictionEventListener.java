package me.fulcanelly.tgbridge.boundaryguard.listeners;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

@RequiredArgsConstructor
public final class RestrictionEventListener implements Listener {

    private final RestrictionService restrictions;
    private final TelegramLinkStatusService telegramLinkStatus;
    private final MinecraftMessageService messages;

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restrictions.refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        telegramLinkStatus.forget(player);
        messages.forget(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !restrictions.blocksContainerUse(player, event.getInventory().getType())) {
            return;
        }
        event.setCancelled(true);
        messages.sendContainerBlocked(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || !restrictions.blocksContainerUse(player, event.getView().getTopInventory().getType())) {
            return;
        }
        event.setCancelled(true);
        messages.sendContainerBlocked(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
            || !restrictions.blocksContainerUse(player, event.getView().getTopInventory().getType())) {
            return;
        }
        event.setCancelled(true);
        messages.sendContainerBlocked(player);
    }

}
