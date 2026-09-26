package me.fulcanelly.tgbridge.boundaryguard.services.conditions;

/**
 * A stable, semantic identifier for why a condition is unmet - decoupled from
 * whether, or how, it ends up shown to a player. Mapping a reason to an actual
 * message (key, locale, wording) is the messages layer's job, not this one's.
 */
public enum ConditionReason {
    TELEGRAM_LINKING,
    PLAYTIME
}
