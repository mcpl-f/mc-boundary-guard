package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.List;

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
}
