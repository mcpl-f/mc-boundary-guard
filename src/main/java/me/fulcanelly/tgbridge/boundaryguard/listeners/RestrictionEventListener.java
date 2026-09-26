package me.fulcanelly.tgbridge.boundaryguard.listeners;

import java.util.List;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionEffect;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerAnimationEvent;
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
        messages.sendContainerBlocked(player, restrictions.unmetReason(player));
    }

    // Adventure Mode itself is silent - the player just finds themselves unable to
    // interact with the world, with no message from this plugin explaining why.
    // These handlers close that gap: once right when they're switched into it,
    // and again on every attempt to interact with the world while in it.

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        if (event.getNewGameMode() == GameMode.ADVENTURE) {
            remindIfRestricted(event.getPlayer());
        }
    }

    // NOT BlockBreakEvent: in Adventure Mode, vanilla's CanDestroy check rejects a
    // break attempt before a BlockBreakEvent is ever created, so a denied attempt
    // never reaches a BlockBreakEvent listener (confirmed live - see
    // features/restriction-condition-feedback.md). PlayerAnimationEvent is the
    // arm-swing itself, which fires unconditionally on left-click; it can't tell
    // whether they were aiming at a block or just swinging at air, but that's an
    // acceptable trade for getting a signal at all.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onArmSwing(PlayerAnimationEvent event) {
        remindIfBlockedByAdventureMode(event.getPlayer());
    }

    // Placing likely has the same CanPlaceOn gap as breaking, unconfirmed - kept
    // as-is since only the break case has been observed failing so far.
    @EventHandler(priority = EventPriority.MONITOR)
    public void onBlockPlace(BlockPlaceEvent event) {
        remindIfBlockedByAdventureMode(event.getPlayer());
    }

    private void remindIfBlockedByAdventureMode(Player player) {
        if (player.getGameMode() == GameMode.ADVENTURE) {
            remindIfRestricted(player);
        }
    }

    // Must call isRestricted(), never refresh(), here. Sequence if it did:
    // 1. AdventureModeRestriction.refresh() calls player.setGameMode(ADVENTURE).
    // 2. Bukkit fires PlayerGameModeChangeEvent from inside that same call,
    //    before the player's mode is actually updated - so getGameMode() still
    //    returns the OLD mode at this point.
    // 3. onGameModeChange (below) handles that event and would call
    //    restrictions.refresh(player) again.
    // 4. That re-runs AdventureModeRestriction.refresh(), which still sees the
    //    old mode, so it calls setGameMode(ADVENTURE) again -> back to step 2.
    // This repeats until the stack overflows and the server thread crashes
    // (happened on a live server; see features/restriction-condition-feedback.md).
    // isRestricted() only reads accessCondition.isMet() - it never touches a
    // Restriction strategy, so it cannot re-enter this loop.
    //
    // Only RestrictionEffect.INTERACT_FREELY is ever passed here, not
    // restrictions.activeEffects() - every caller of this method (gamemode
    // change, arm swing, block place) is, by construction, specifically about
    // Adventure Mode, so that's the one thing relevant to what the player just
    // did, regardless of which other strategies happen to also be enabled.
    private void remindIfRestricted(Player player) {
        if (restrictions.isRestricted(player)) {
            messages.sendConditionReminder(
                    player, restrictions.unmetReason(player), List.of(RestrictionEffect.INTERACT_FREELY));
        }
    }
}
