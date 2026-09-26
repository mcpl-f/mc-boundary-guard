package me.fulcanelly.tgbridge.boundaryguard.jobs;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Reminds every currently-restricted online player what they still need to
 * satisfy, independent of {@link RestrictionRefreshJob}. An idle player - not
 * moving, not touching a block or inventory - never triggers any of
 * {@code RestrictionEventListener}'s other reminder paths, so without this job
 * they'd get no feedback at all until they happened to do something.
 *
 * Uses {@link RestrictionService#isRestricted}, not {@code refresh}, since this
 * only needs to read condition state - it has nothing to do with applying a
 * {@code Restriction} strategy's side effects (that's {@link
 * RestrictionRefreshJob}'s job).
 */
@RequiredArgsConstructor
public final class ConditionReminderJob implements Runnable {

    private final RestrictionService restrictions;
    private final MinecraftMessageService messages;

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (restrictions.isRestricted(player)) {
                messages.sendConditionReminder(player, restrictions.unmetReasons(player));
            }
        }
    }
}
