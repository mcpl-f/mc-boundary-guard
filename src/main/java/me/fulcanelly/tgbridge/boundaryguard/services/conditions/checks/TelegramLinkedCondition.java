package me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks;

import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;

import org.bukkit.entity.Player;

public final class TelegramLinkedCondition implements Condition {

    private final TelegramLinkStatusService telegramLinkStatus;

    public TelegramLinkedCondition(TelegramLinkStatusService telegramLinkStatus) {
        this.telegramLinkStatus = telegramLinkStatus;
    }

    @Override
    public boolean isMet(Player player) {
        return telegramLinkStatus.isLinked(player);
    }
}
