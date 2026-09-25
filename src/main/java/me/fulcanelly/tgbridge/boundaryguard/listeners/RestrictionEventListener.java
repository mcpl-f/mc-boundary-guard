package me.fulcanelly.tgbridge.boundaryguard.listeners;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;
import me.fulcanelly.tgbridge.boundaryguard.utils.MessageLocalizer;

import org.bukkit.entity.HumanEntity;
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
    private final MessageLocalizer messages;
    private final long warningCooldownMillis;
    private final Map<UUID, Long> lastWarningAt = new HashMap<>();

    public RestrictionEventListener(
            RestrictionService restrictions,
            TelegramLinkStatusService telegramLinkStatus,
            MessageLocalizer messages,
            long warningCooldownMillis) {
        this.restrictions = restrictions;
        this.telegramLinkStatus = telegramLinkStatus;
        this.messages = messages;
        this.warningCooldownMillis = warningCooldownMillis;
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
        lastWarningAt.remove(player.getUniqueId());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)
                || !isContainer(event.getInventory().getType())
                || restrictions.allowsContainerUse(player)) {
            return;
        }
        event.setCancelled(true);
        warn(player);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!isContainer(event.getView().getTopInventory().getType())) {
            return;
        }
        blockIfRestricted(event.getWhoClicked());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!isContainer(event.getView().getTopInventory().getType())) {
            return;
        }
        blockIfRestricted(event.getWhoClicked());
    }

    private void blockIfRestricted(HumanEntity human) {
        if (!(human instanceof Player player) || restrictions.allowsContainerUse(player)) {
            return;
        }
        player.closeInventory();
        warn(player);
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

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        long last = lastWarningAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < warningCooldownMillis) {
            return;
        }
        lastWarningAt.put(player.getUniqueId(), now);
        player.sendMessage(messages.get(player, "container-blocked"));
    }
}
