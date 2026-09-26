package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks.InSpawnRadiusCondition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks.MinimumPlaytimeCondition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.checks.TelegramLinkedCondition;

/**
 * Builds runtime conditions from the normalized {@code rules.condition} YAML tree.
 * Group nodes use {@code all} or {@code any}; leaf nodes describe one check and
 * its options, for example {@code time-played-limit: {hours: 2.5}}.
 */
@RequiredArgsConstructor
public final class ConditionFactory {

    private static final String ALL = "all";
    private static final String ANY = "any";

    private static final String TELEGRAM_LINK_CHECK = "tg-linking-check";
    private static final String PLAYTIME_LIMIT = "time-played-limit";
    private static final String IN_SPAWN_RADIUS = "in-spawn-radius";

    private final TelegramLinkStatusService telegramLinkStatus;

    /**
     * Converts one normalized YAML condition node into its executable rule.
     */
    public Condition fromConfig(Object configNode) {
        if (configNode instanceof Map<?, ?> map) {
            return fromMap(map);
        }

        if (configNode instanceof String name) {
            return createLeaf(name, null);
        }

        throw new IllegalArgumentException("Invalid condition configuration: " + configNode);
    }

    private Condition fromMap(Map<?, ?> config) {
        if (config.size() != 1) {
            throw new IllegalArgumentException("Each condition must contain exactly one key");
        }

        Map.Entry<?, ?> entry = config.entrySet().iterator().next();
        String name = String.valueOf(entry.getKey());
        Object parameters = entry.getValue();

        if (ALL.equals(name)) {
            return composite(name, parameters, ConditionApplier.Operator.ALL);
        }

        if (ANY.equals(name)) {
            return composite(name, parameters, ConditionApplier.Operator.ANY);
        }

        return createLeaf(name, parameters);
    }

    private Condition composite(String name, Object value, ConditionApplier.Operator operator) {
        if (!(value instanceof List<?> childNodes)) {
            throw new IllegalArgumentException(name + " condition must be a list");
        }

        // Parse children through the same entry point so groups can nest to any depth.
        List<Condition> children = new ArrayList<>(childNodes.size());

        for (Object childNode : childNodes) {
            children.add(fromConfig(childNode));
        }

        return new ConditionApplier(operator, children);
    }

    private Condition createLeaf(String name, Object parameters) {
        return switch (name) {
            case TELEGRAM_LINK_CHECK -> new TelegramLinkedCondition(telegramLinkStatus);
            case PLAYTIME_LIMIT -> new MinimumPlaytimeCondition(readHours(parameters));
            case IN_SPAWN_RADIUS -> new InSpawnRadiusCondition(new BoundaryArea(readRadius(parameters)));
            default -> throw new IllegalArgumentException("Unknown condition: " + name);
        };
    }

    private double readHours(Object parameters) {
        if (!(parameters instanceof Map<?, ?> values)) {
            throw new IllegalArgumentException(PLAYTIME_LIMIT + " requires numeric hours");
        }

        Object hoursValue = values.get("hours");
        if (!(hoursValue instanceof Number hours)) {
            throw new IllegalArgumentException(PLAYTIME_LIMIT + " requires numeric hours");
        }

        return hours.doubleValue();
    }

    private double readRadius(Object parameters) {
        if (!(parameters instanceof Number radius)) {
            throw new IllegalArgumentException(IN_SPAWN_RADIUS + " requires a numeric radius");
        }
        return radius.doubleValue();
    }
}
