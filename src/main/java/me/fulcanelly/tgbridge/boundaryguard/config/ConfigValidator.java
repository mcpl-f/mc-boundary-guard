package me.fulcanelly.tgbridge.boundaryguard.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import me.fulcanelly.tgbridge.boundaryguard.integrations.discordsrv.DiscordLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionFactory;

/**
 * Checks {@code rules.omit-restriction-when} and {@code rules.otherwise-apply-restriction-strategies} for structural
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
 * before config validity is even known. {@code discordLinkStatus} is passed
 * through as-is instead: whether DiscordSRV (a soft dependency) is actually
 * installed is exactly what decides whether {@code ds-linking-check} is
 * itself a valid thing to configure, so that one has to reflect reality.
 */
public final class ConfigValidator {

    private final BoundaryGuardConfig config;
    private final Optional<DiscordLinkStatusService> discordLinkStatus;

    public ConfigValidator(BoundaryGuardConfig config, Optional<DiscordLinkStatusService> discordLinkStatus) {
        this.config = config;
        this.discordLinkStatus = discordLinkStatus;
    }

    /** For a config known not to use ds-linking-check (e.g. settings-only tests). */
    public ConfigValidator(BoundaryGuardConfig config) {
        this(config, Optional.empty());
    }

    /**
     * @throws IllegalStateException listing every problem found in {@code
     *     rules.omit-restriction-when} and {@code rules.otherwise-apply-restriction-strategies}, if any.
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
            new ConditionFactory(null, discordLinkStatus).fromConfig(config.conditionDefinition());
        } catch (IllegalArgumentException exception) {
            problems.add("rules.omit-restriction-when: " + exception.getMessage());
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
