package me.fulcanelly.tgbridge.boundaryguard.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.RequiredArgsConstructor;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

/** The single adapter from Bukkit YAML configuration to plugin settings. */
@RequiredArgsConstructor
public final class BoundaryGuardConfig {

    // Used only when rules.condition has no in-spawn-radius leaf at all, so a
    // keep-on-spawn: true with nothing to size it still has a sane area.
    private static final double DEFAULT_SPAWN_RADIUS = 256.0;

    private static final long TICKS_PER_SECOND = 20L;

    private final FileConfiguration source;

    /**
     * The shared spawn-area radius, read from the {@code in-spawn-radius} leaf
     * inside {@code rules.condition} (wherever it is in the tree - {@code all},
     * {@code any}, nested or not). There is deliberately no separate
     * {@code keep-on-spawn.radius}: the area a player must stay inside to count as
     * unrestricted, and the area {@code keep-on-spawn} physically confines them
     * to, are the same area, so it's defined once.
     */
    public double spawnRadius() {
        double found = findInSpawnRadius(source.get("rules.condition"));
        return Double.isNaN(found) ? DEFAULT_SPAWN_RADIUS : found;
    }

    private double findInSpawnRadius(Object node) {
        if (node instanceof ConfigurationSection section) {
            return findInSpawnRadius(section.getValues(false));
        }
        if (node instanceof Map<?, ?> map) {
            Object radius = map.get("in-spawn-radius");
            if (radius instanceof Number number) {
                return number.doubleValue();
            }
            for (Object value : map.values()) {
                double found = findInSpawnRadius(value);
                if (!Double.isNaN(found)) {
                    return found;
                }
            }
            return Double.NaN;
        }
        if (node instanceof List<?> list) {
            for (Object item : list) {
                double found = findInSpawnRadius(item);
                if (!Double.isNaN(found)) {
                    return found;
                }
            }
        }
        return Double.NaN;
    }

    public boolean spawnBoundaryEnabled() {
        return source.getBoolean("rules.strategies.keep-on-spawn", false);
    }

    public boolean adventureModeEnabled() {
        return source.getBoolean("rules.strategies.switch-2-adventure-mode", false);
    }

    public boolean containerUseForbidden() {
        return source.getBoolean("rules.strategies.forbid-container-use", false);
    }

    public long warningCooldownMillis() {
        return Math.round(source.getDouble("message-cooldown-millis", 500.0));
    }

    public long restrictionRefreshIntervalTicks() {
        return Math.max(1L, source.getLong("restriction-refresh-interval-ticks", 60L));
    }

    public long telegramRecheckCooldownMillis() {
        return Math.max(1L, source.getLong("telegram-recheck-cooldown-millis", 3000L));
    }

    /** How often, in ticks, an idle restricted player is reminded what they still need to satisfy. */
    public long conditionReminderIntervalTicks() {
        return Math.max(1L, source.getLong("condition-reminder.interval-seconds", 2L) * TICKS_PER_SECOND);
    }

    /** Whether the idle-player reminder job should be scheduled at all. */
    public boolean conditionReminderEnabled() {
        return source.getBoolean("condition-reminder.enabled", true);
    }

    public Object conditionDefinition() {
        return normalize(source.get("rules.condition"));
    }

    public String defaultLocale() {
        return source.getString("default-locale", "en");
    }

    public String message(String locale, String key) {
        String localized = source.getString("messages." + locale + "." + key);
        if (localized != null) {
            return localized;
        }

        String fallback = source.getString("messages." + defaultLocale() + "." + key);
        if (fallback != null) {
            return fallback;
        }

        return source.getString("messages.en." + key, key);
    }

    /** Whether {@code key} resolves to an actual configured value (not the fallback echo). */
    public boolean hasMessage(String locale, String key) {
        return source.isSet("messages." + locale + "." + key)
                || source.isSet("messages." + defaultLocale() + "." + key)
                || source.isSet("messages.en." + key);
    }

    private Object normalize(Object value) {
        if (value instanceof ConfigurationSection section) {
            return normalize(section.getValues(false));
        }
        if (value instanceof Map<?, ?> values) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            values.forEach((key, entry) -> normalized.put(String.valueOf(key), normalize(entry)));
            return normalized;
        }
        if (value instanceof List<?> values) {
            List<Object> normalized = new ArrayList<>(values.size());
            values.forEach(entry -> normalized.add(normalize(entry)));
            return normalized;
        }
        return value;
    }
}
