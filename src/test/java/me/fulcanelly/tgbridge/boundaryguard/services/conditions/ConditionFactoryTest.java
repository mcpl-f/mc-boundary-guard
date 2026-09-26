package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.List;
import java.util.Map;

import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;

import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConditionFactoryTest {

    private static final int TICKS_PER_HOUR = 20 * 60 * 60;

    @Mock
    private TelegramLinkStatusService telegramLinkStatus;
    @Mock
    private Player player;

    @Test
    void allFailsAsSoonAsOneChildFailsWithoutEvaluatingTheRest() {
        ConditionFactory factory = new ConditionFactory(telegramLinkStatus);

        Object tree = Map.of("all", List.of(
                Map.of("time-played-limit", Map.of("hours", 10.0)),
                "tg-linking-check"));

        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(0);

        assertFalse(factory.fromConfig(tree).isMet(player));
        // The nested tg-linking-check is never reached once the first child fails,
        // so telegramLinkStatus.isLinked() is intentionally left unstubbed here.
    }

    @Test
    void anySucceedsAsSoonAsOneChildSucceeds() {
        ConditionFactory factory = new ConditionFactory(telegramLinkStatus);

        Object tree = Map.of("any", List.of(
                "tg-linking-check",
                Map.of("time-played-limit", Map.of("hours", 999.0))));

        when(telegramLinkStatus.isLinked(player)).thenReturn(true);

        assertTrue(factory.fromConfig(tree).isMet(player));
    }

    @Test
    void allContainingAnyEvaluatesTheNestedGroupAsOneChild() {
        ConditionFactory factory = new ConditionFactory(telegramLinkStatus);

        // all: [time-played-limit(1h), any: [tg-linking-check, time-played-limit(0h)]]
        Object tree = Map.of("all", List.of(
                Map.of("time-played-limit", Map.of("hours", 1.0)),
                Map.of("any", List.of(
                        "tg-linking-check",
                        Map.of("time-played-limit", Map.of("hours", 0.0))))));

        when(player.getStatistic(Statistic.PLAY_ONE_MINUTE)).thenReturn(TICKS_PER_HOUR);
        when(telegramLinkStatus.isLinked(player)).thenReturn(false);

        // Playtime clears the outer "all"; the inner "any" is satisfied by its
        // always-met 0h leaf, independent of the (unmet) Telegram check.
        assertTrue(factory.fromConfig(tree).isMet(player));
    }
}
