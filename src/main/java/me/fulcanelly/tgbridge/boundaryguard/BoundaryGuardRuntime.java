package me.fulcanelly.tgbridge.boundaryguard;

import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

import org.bukkit.scheduler.BukkitTask;

/**
 * Owns the periodic refresh task started during startup.
 *
 * There is no other startup-created state to restore on shutdown: restriction
 * strategies keep no per-player state (see {@link
 * me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction}), and
 * {@code TelegramLinkStatusService}/{@code MinecraftMessageService} are rebuilt
 * fresh on every {@code onEnable}, so their previous instance's cache is simply
 * dropped rather than needing an explicit clear on disable.
 *
 * Bukkit already cancels a plugin's own scheduled tasks when it disables the
 * plugin; this cancel is a cheap, explicit backstop rather than the only path
 * that stops the task.
 */
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public final class BoundaryGuardRuntime {

    private final BukkitTask refreshTask;

    public void stop() {
        refreshTask.cancel();
    }
}
