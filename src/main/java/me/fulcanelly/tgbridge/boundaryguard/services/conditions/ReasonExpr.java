package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

import java.util.List;

/**
 * A small expression tree describing why a player is currently restricted,
 * preserving the {@code all}/{@code any} structure of {@code rules.condition}
 * instead of flattening it into a plain list. That structure is what lets a
 * message join multiple reasons with "and"/"or" correctly instead of just
 * concatenating them.
 */
public sealed interface ReasonExpr {

    record Leaf(ConditionReason reason) implements ReasonExpr {
    }

    record Group(ConditionApplier.Operator operator, List<ReasonExpr> children) implements ReasonExpr {
    }
}
