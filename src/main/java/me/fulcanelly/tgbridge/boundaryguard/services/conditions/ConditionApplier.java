package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.ArrayList;
import java.util.List;

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
    public List<ConditionReason> unmetReasons(Player player) {
        // ALL: every unmet child still needs fixing. ANY: nothing to say once one
        // child is already met; otherwise every child is a valid alternative to show.
        if (operator == Operator.ANY && isMet(player)) {
            return List.of();
        }

        List<ConditionReason> reasons = new ArrayList<>();
        for (Condition condition : conditions) {
            reasons.addAll(condition.unmetReasons(player));
        }
        return reasons;
    }
}
