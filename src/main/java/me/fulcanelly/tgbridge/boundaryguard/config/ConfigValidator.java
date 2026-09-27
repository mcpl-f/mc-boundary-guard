package me.fulcanelly.tgbridge.boundaryguard.config;

import java.util.ArrayList;
import java.util.List;

import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionFactory;

/**
 * Checks {@code rules.condition} and {@code rules.strategies} for structural
 * problems before anything else gets built, so a broken config.yml fails with
 * one clear, complete list of what's wrong instead of a stack trace from
 * whichever factory happened to choke on it first (or, worse, a plugin that
 * starts in a half-built state).
 *
 * {@link ConditionFactory} and {@link RestrictionFactory} already throw on
 * bad input - this doesn't duplicate their rules, it just runs both up front,
 * collects every problem rather than stopping at the first, and reports them
 * together. {@link ConditionFactory} is given a {@code null}
 * {@code TelegramLinkStatusService}: constructing a {@code Condition} tree
 * never calls it (see {@code TelegramLinkedCondition}, which only stores the
 * reference), and getting a real one this early would mean finding tg-bridge
 * before config validity is even known.
 */
public final class ConfigValidator {

    private final BoundaryGuardConfig config;

    public ConfigValidator(BoundaryGuardConfig config) {
        this.config = config;
    }

    /**
     * @throws IllegalStateException listing every problem found in {@code
     *     rules.condition} and {@code rules.strategies}, if any.
     */
    public void validate() {
        List<String> problems = new ArrayList<>();
        validateCondition(problems);
        validateRestrictions(problems);

        if (!problems.isEmpty()) {
            throw new IllegalStateException(
                    "Invalid rules configuration in config.yml:\n - " + String.join("\n - ", problems));
        }
    }

    private void validateCondition(List<String> problems) {
        try {
            new ConditionFactory(null).fromConfig(config.conditionDefinition());
        } catch (IllegalArgumentException exception) {
            problems.add("rules.condition: " + exception.getMessage());
        }
    }

    private void validateRestrictions(List<String> problems) {
        try {
            new RestrictionFactory(config).create();
        } catch (IllegalStateException exception) {
            problems.add(exception.getMessage());
        }
    }
}
