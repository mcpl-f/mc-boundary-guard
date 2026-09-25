package me.fulcanelly.tgbridge.boundaryguard.services.utils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import me.fulcanelly.tgbridge.boundaryguard.utils.MessageLocalizer;

import org.bukkit.entity.Player;

public final class PlayerWarningService {

    private final MessageLocalizer messages;
    private final long cooldownMillis;
    private final Map<UUID, Long> lastWarningAt = new HashMap<>();

    public PlayerWarningService(MessageLocalizer messages, long cooldownMillis) {
        this.messages = messages;
        this.cooldownMillis = cooldownMillis;
    }

    public boolean tryWarn(Player player) {
        long now = System.currentTimeMillis();
        long last = lastWarningAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < cooldownMillis) {
            return false;
        }

        lastWarningAt.put(player.getUniqueId(), now);
        return true;
    }

    public void send(Player player, String messageKey) {
        if (tryWarn(player)) {
            player.sendMessage(message(player, messageKey));
        }
    }

    public String message(Player player, String messageKey) {
        return messages.get(player, messageKey);
    }

    public void forget(Player player) {
        lastWarningAt.remove(player.getUniqueId());
    }
}
