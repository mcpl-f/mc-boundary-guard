package me.fulcanelly.tgbridge.boundaryguard.jobs;

import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public final class RestrictionRefreshJob implements Runnable {

    private final RestrictionService restrictions;

    public RestrictionRefreshJob(RestrictionService restrictions) {
        this.restrictions = restrictions;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restrictions.refresh(player);
        }
    }
}
