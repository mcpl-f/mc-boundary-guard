package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ConditionApplier implements Condition {

    public enum Operator {
        ALL,
        ANY
    }

    private final Operator operator;
    private final List<Condition> conditions;

    public ConditionApplier(Operator operator, List<Condition> conditions) {
        this.operator = operator;
        this.conditions = List.copyOf(conditions);
    }

    @Override
    public boolean isMet(Player player) {
        if (operator == Operator.ALL) {
            return conditions.stream().allMatch(condition -> condition.isMet(player));
        }
        return conditions.stream().anyMatch(condition -> condition.isMet(player));
    }

    @Override
    public boolean isMet(Player player, Location location) {
        if (operator == Operator.ALL) {
            return conditions.stream().allMatch(condition -> condition.isMet(player, location));
        }
        return conditions.stream().anyMatch(condition -> condition.isMet(player, location));
    }

    @Override
    public Optional<ReasonExpr> unmetReason(Player player) {
        // ALL: every unmet child still needs fixing. ANY: nothing to say once one
        // child is already met; otherwise every child is a valid alternative to show.
        if (operator == Operator.ANY && isMet(player)) {
            return Optional.empty();
        }

        List<ReasonExpr> children = new ArrayList<>();
        for (Condition condition : conditions) {
            condition.unmetReason(player).ifPresent(children::add);
        }

        if (children.isEmpty()) {
            return Optional.empty();
        }
        if (children.size() == 1) {
            // No group of one - nothing to join, so no join word would ever be used.
            return Optional.of(children.get(0));
        }
        return Optional.of(new ReasonExpr.Group(operator, children));
    }
}
