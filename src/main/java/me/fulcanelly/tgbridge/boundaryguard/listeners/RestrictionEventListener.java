package me.fulcanelly.tgbridge.boundaryguard.listeners;

import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;
import me.fulcanelly.tgbridge.boundaryguard.services.utils.PlayerWarningService;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class RestrictionEventListener implements Listener {

    private final RestrictionService restrictions;
    private final TelegramLinkStatusService telegramLinkStatus;
    private final PlayerWarningService warnings;

    public RestrictionEventListener(
            RestrictionService restrictions,
            TelegramLinkStatusService telegramLinkStatus,
            PlayerWarningService warnings) {
        this.restrictions = restrictions;
        this.telegramLinkStatus = telegramLinkStatus;
        this.warnings = warnings;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        restrictions.refresh(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        restrictions.forget(player);
        telegramLinkStatus.forget(player);
        warnings.forget(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !isContainer(event.getInventory().getType())
                || restrictions.allowsContainerUse(player)) {
            return;
        }
        event.setCancelled(true);
        warnings.send(player, "container-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isContainer(event.getView().getTopInventory().getType())
                || !(event.getWhoClicked() instanceof Player player)
                || restrictions.allowsContainerUse(player)) {
            return;
        }
        event.setCancelled(true);
        warnings.send(player, "container-blocked");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isContainer(event.getView().getTopInventory().getType())
                || !(event.getWhoClicked() instanceof Player player)
                || restrictions.allowsContainerUse(player)) {
            return;
        }
        event.setCancelled(true);
        warnings.send(player, "container-blocked");
    }

    private boolean isContainer(InventoryType type) {
        switch (type) {
            case CHEST:
            case DISPENSER:
            case DROPPER:
            case FURNACE:
            case BREWING:
            case ENDER_CHEST:
            case HOPPER:
            case SHULKER_BOX:
            case BARREL:
            case BLAST_FURNACE:
            case SMOKER:
                return true;
            default:
                return false;
        }
    }

}
