package me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.integrations.discordsrv.DiscordLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;

import org.bukkit.entity.Player;

/** Met once a player has linked their Discord account via DiscordSRV. */
@RequiredArgsConstructor
public final class DiscordLinkedCondition implements Condition {

    private final DiscordLinkStatusService discordLinkStatus;

    @Override
    public boolean isMet(Player player) {
        return discordLinkStatus.isLinked(player);
    }

    @Override
    public Optional<ConditionReason> reason() {
        return Optional.of(ConditionReason.DISCORD_LINKING);
    }
}
