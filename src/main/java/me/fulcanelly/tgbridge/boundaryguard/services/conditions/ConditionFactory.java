package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks.MinimumPlaytimeCondition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks.TelegramLinkedCondition;

@RequiredArgsConstructor
public final class ConditionFactory {

    private final TelegramLinkStatusService telegramLinkStatus;

    public Condition fromConfig(Object configNode) {
        if (configNode instanceof Map<?, ?> map) {
            return fromMap(map);
        }
        if (configNode instanceof String name) {
            return leaf(name, null);
        }
        throw new IllegalArgumentException("Invalid condition configuration: " + configNode);
    }

    private Condition fromMap(Map<?, ?> config) {
        if (config.size() != 1) {
            throw new IllegalArgumentException("Each condition must contain exactly one key");
        }

        Map.Entry<?, ?> entry = config.entrySet().iterator().next();
        String name = String.valueOf(entry.getKey());
        Object value = entry.getValue();

        if ("all".equals(name) || "any".equals(name)) {
            if (!(value instanceof List<?> children)) {
                throw new IllegalArgumentException(name + " condition must be a list");
            }
            List<Condition> conditions = new ArrayList<>(children.size());
            for (Object child : children) {
                conditions.add(fromConfig(child));
            }
            ConditionApplier.Operator operator = "all".equals(name)
                    ? ConditionApplier.Operator.ALL
                    : ConditionApplier.Operator.ANY;
            return new ConditionApplier(operator, conditions);
        }

        return leaf(name, value);
    }

    private Condition leaf(String name, Object value) {
        if ("tg-linking-check".equals(name)) {
            return new TelegramLinkedCondition(telegramLinkStatus);
        }
        if ("time-played-limit".equals(name)) {
            Object hoursValue = value instanceof Map<?, ?> map ? map.get("hours") : null;
            if (!(hoursValue instanceof Number hours)) {
                throw new IllegalArgumentException("time-played-limit requires numeric hours");
            }
            return new MinimumPlaytimeCondition(hours.doubleValue());
        }
        throw new IllegalArgumentException("Unknown condition: " + name);
    }
}
