package me.fulcanelly.tgbridge.boundaryguard;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;

import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the scheduled work and player state created during startup.
 *
 * Bukkit calls onDisable independently of startup, so keeping task cancellation
 * and per-player restoration together prevents temporary restrictions leaking
 * across a plugin reload.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class BoundaryGuardRuntime {

    private final BoundaryGuardPlugin plugin;
    private final TelegramLinkStatusService telegramLinkStatus;
    private final BukkitTask refreshTask;

    public void stop() {
        refreshTask.cancel();
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            telegramLinkStatus.forget(player);
        }
    }
}
