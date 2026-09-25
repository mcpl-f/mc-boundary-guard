package me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks;

import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;

public final class MinimumPlaytimeCondition implements Condition {

    private static final long TICKS_PER_HOUR = 20L * 60L * 60L;

    private final long minimumTicks;

    public MinimumPlaytimeCondition(double hours) {
        if (!Double.isFinite(hours) || hours < 0.0) {
            throw new IllegalArgumentException("Playtime hours must be finite and non-negative");
        }
        this.minimumTicks = (long) Math.ceil(hours * TICKS_PER_HOUR);
    }

    @Override
    public boolean isMet(Player player) {
        return player.getStatistic(Statistic.PLAY_ONE_MINUTE) >= minimumTicks;
    }
}
