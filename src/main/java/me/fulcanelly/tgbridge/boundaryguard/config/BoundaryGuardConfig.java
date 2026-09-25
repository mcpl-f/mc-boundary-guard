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

    private static final double DEFAULT_ALLOWED_RADIUS = 256.0;

    private final FileConfiguration source;

    public double allowedRadius() {
        return source.getDouble("allowed-radius", DEFAULT_ALLOWED_RADIUS);
    }

    public double spawnRadius() {
        return source.getDouble("rules.strategies.keep-on-spawn.radius", allowedRadius());
    }

    public boolean spawnBoundaryEnabled() {
        return source.isConfigurationSection("rules.strategies.keep-on-spawn");
    }

    public boolean adventureModeEnabled() {
        return source.isConfigurationSection("rules.strategies.switch-2-adventure-mode");
    }

    public boolean adventureModeEverywhere() {
        return source.getBoolean("rules.strategies.switch-2-adventure-mode.everywhere", false);
    }

    public double adventureModeRadius() {
        return source.getDouble("rules.strategies.switch-2-adventure-mode.radius", allowedRadius());
    }

    public boolean containerUseForbidden() {
        return source.getBoolean("rules.strategies.forbid-container-use", false);
    }

    public long warningCooldownMillis() {
        return Math.round(source.getDouble("message-cooldown-millis", 500.0));
    }

    public long telegramCheckIntervalTicks() {
        return Math.max(1L, source.getLong("rules.telegram-check-interval-ticks", 60L));
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
