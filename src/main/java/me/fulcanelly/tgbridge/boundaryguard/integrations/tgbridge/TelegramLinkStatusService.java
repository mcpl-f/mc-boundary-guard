package me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;

import org.bukkit.entity.Player;

/**
 * Tracks whether a player has linked Telegram, so restriction refreshes don't have
 * to query tg-bridge every time.
 *
 * A positive result is cached for the rest of the login session, since linking
 * cannot be undone in-game. A negative result is cached only for
 * {@code negativeCacheMillis}, so an unlinked player is re-checked periodically
 * instead of hitting {@code reception.getTgByUser} on every single refresh.
 */
public final class TelegramLinkStatusService {

    private final SignupLoginReception reception;
    private final long negativeCacheMillis;
    private final Set<UUID> linkedPlayers = new HashSet<>();
    private final Map<UUID, Long> lastUnlinkedCheckAt = new HashMap<>();

    public TelegramLinkStatusService(SignupLoginReception reception, long negativeCacheMillis) {
        this.reception = reception;
        this.negativeCacheMillis = Math.max(1L, negativeCacheMillis);
    }

    public boolean isLinked(Player player) {
        UUID playerId = player.getUniqueId();
        if (linkedPlayers.contains(playerId)) {
            return true;
        }

        long now = System.currentTimeMillis();
        Long lastCheck = lastUnlinkedCheckAt.get(playerId);
        if (lastCheck != null && now - lastCheck < negativeCacheMillis) {
            return false;
        }

        lastUnlinkedCheckAt.put(playerId, now);
        if (reception.getTgByUser(player.getName()).isPresent()) {
            linkedPlayers.add(playerId);
            lastUnlinkedCheckAt.remove(playerId);
            return true;
        }
        return false;
    }

    public void forget(Player player) {
        UUID playerId = player.getUniqueId();
        linkedPlayers.remove(playerId);
        lastUnlinkedCheckAt.remove(playerId);
    }
}
