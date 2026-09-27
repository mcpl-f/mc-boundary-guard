package me.fulcanelly.tgbridge.boundaryguard.integrations.discordsrv;

import github.scarsz.discordsrv.DiscordSRV;

import org.bukkit.entity.Player;

/**
 * Checks whether a player has linked their Discord account, via DiscordSRV's
 * own {@code AccountLinkManager} - which already keeps this in memory
 * (populated at link time and loaded on startup), so unlike tg-bridge's
 * {@code TelegramLinkStatusService} there's no caching to add here: every
 * call is already a plain in-JVM map lookup, not a network/database
 * round trip.
 */
public final class DiscordLinkStatusService {

    private final DiscordSRV discordSrv;

    public DiscordLinkStatusService(DiscordSRV discordSrv) {
        this.discordSrv = discordSrv;
    }

    public boolean isLinked(Player player) {
        return discordSrv.getAccountLinkManager().getDiscordId(player.getUniqueId()) != null;
    }
}
