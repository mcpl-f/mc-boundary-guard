package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import org.bukkit.entity.Player;

public interface Condition {

    boolean isMet(Player player);
}
