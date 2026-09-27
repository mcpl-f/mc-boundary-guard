package me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks;

import java.util.Optional;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;

import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class TelegramLinkedCondition implements Condition {

    private final TelegramLinkStatusService telegramLinkStatus;

    @Override
    public boolean isMet(Player player) {
        return telegramLinkStatus.isLinked(player);
    }

    @Override
    public Optional<ConditionReason> reason() {
        return Optional.of(ConditionReason.TELEGRAM_LINKING);
    }
}
