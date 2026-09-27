package me.fulcanelly.tgbridge.boundaryguard.jobs;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class RestrictionRefreshJob implements Runnable {

    private final RestrictionService restrictions;

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            restrictions.refresh(player);
        }
    }
}
