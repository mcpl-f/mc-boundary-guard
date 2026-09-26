package me.fulcanelly.tgbridge.boundaryguard.listeners;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
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
        if (event.getPlayer() instanceof Player player) {
            blockIfRestricted(event, player, event.getInventory().getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            blockIfRestricted(event, player, event.getView().getTopInventory().getType());
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player) {
            blockIfRestricted(event, player, event.getView().getTopInventory().getType());
        }
    }

    private void blockIfRestricted(Cancellable event, Player player, InventoryType type) {
        if (!restrictions.blocksContainerUse(player, type)) {
            return;
        }
        event.setCancelled(true);
        messages.sendContainerBlocked(player);
    }

    // Adventure Mode itself is silent - the player just finds themselves unable to
    // interact with the world, with no message from this plugin explaining why.
    // These three handlers close that gap: once right when they're switched into
    // it, and again on every attempt to break/place a block while in it, since a
    // Bukkit event still fires there regardless of whether vanilla's own
    // CanDestroy/CanPlaceOn check ends up allowing the interaction.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.ADVENTURE) {
            remindIfRestricted(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockBreak(BlockBreakEvent event) {
        remindIfBlockedByAdventureMode(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        remindIfBlockedByAdventureMode(event.getPlayer());
    }

    private void remindIfBlockedByAdventureMode(Player player) {
        if (player.getGameMode() == GameMode.ADVENTURE) {
            remindIfRestricted(player);
        }
    }

    private void remindIfRestricted(Player player) {
        if (restrictions.refresh(player)) {
            messages.sendConditionReminder(player, restrictions.unmetReasons(player));
        }
    }
}
