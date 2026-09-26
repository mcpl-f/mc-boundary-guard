package me.fulcanelly.tgbridge.boundaryguard.services.restrictions;

/**
 * A stable, semantic identifier for what a {@link Restriction} strategy
 * currently blocks - decoupled from whether, or how, it's described to a
 * player. Mapping an effect to actual wording is the messages layer's job, not
 * this one's (mirrors {@code ConditionReason} on the conditions side).
 */
public enum RestrictionEffect {
    LEAVE_SPAWN,
    USE_CONTAINERS,
    INTERACT_FREELY
}
