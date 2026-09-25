package me.fulcanelly.tgbridge.boundaryguard.utils;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public final class MessageLocalizer {

    private final FileConfiguration config;
    private final String defaultLocale;

    public MessageLocalizer(FileConfiguration config) {
        this.config = config;
        this.defaultLocale = normalizeLocale(config.getString("default-locale", "en"));
    }

    public String get(Player player, String key) {
        return get(playerLocale(player), key);
    }

    public String getDefault(String key) {
        return get(defaultLocale, key);
    }

    private String get(String locale, String key) {
        String path = "messages." + locale + "." + key;

        if (config.contains(path)) {
            return config.getString(path);
        }

        String fallbackPath = "messages." + defaultLocale + "." + key;

        if (config.contains(fallbackPath)) {
            return config.getString(fallbackPath);
        }

        return config.getString("messages.en." + key, key);
    }

    private String playerLocale(Player player) {
        return normalizeLocale(player.getLocale());
    }

    private String normalizeLocale(String locale) {
        if (locale == null || locale.isEmpty()) {
            return "en";
        }

        return locale.toLowerCase().split("[_-]", 2)[0];
    }
}