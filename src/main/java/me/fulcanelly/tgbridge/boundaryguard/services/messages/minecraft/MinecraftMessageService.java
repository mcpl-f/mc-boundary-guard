package me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import org.bukkit.entity.Player;

/** Builds and sends localized Minecraft messages, including cooldown-limited warnings. */
public final class MinecraftMessageService {

    private final BoundaryGuardConfig config;
    private final long warningCooldownMillis;
    private final String defaultLocale;
    private final Map<UUID, Long> lastWarningAt = new HashMap<>();

    public MinecraftMessageService(BoundaryGuardConfig config, long warningCooldownMillis) {
        this.config = config;
        this.warningCooldownMillis = warningCooldownMillis;
        this.defaultLocale = normalizeLocale(config.defaultLocale());
    }

    public String get(Player player, String key) {
        return config.message(playerLocale(player), key);
    }

    public String getDefault(String key) {
        return config.message(defaultLocale, key);
    }

    public void sendContainerBlocked(Player player) {
        if (tryWarn(player)) {
            player.sendMessage(ChatColor.RED + get(player, "container-blocked"));
        }
    }

    public void sendBoundaryBlocked(Player player) {
        if (tryWarn(player)) {
            player.spigot().sendMessage(buildBoundaryBlockedMessage(player));
        }
    }

    /**
     * Reminds a restricted player what they still need to satisfy - e.g. right
     * after they're switched to Adventure Mode, or when they try to interact with
     * the world while in it. {@code reasons} come from
     * {@code RestrictionService#unmetReasons}; a blank list sends nothing.
     */
    public void sendConditionReminder(Player player, List<ConditionReason> reasons) {
        if (reasons.isEmpty() || !tryWarn(player)) {
            return;
        }

        String combined = reasons.stream()
                .distinct()
                .map(reason -> get(player, messageKeyFor(reason)))
                .collect(Collectors.joining("\n"));
        player.sendMessage(ChatColor.RED + combined);
    }

    // The only place that needs to know a ConditionReason maps to a messages.*
    // key - the conditions layer itself stays unaware config.yml even has a
    // messages section.
    private String messageKeyFor(ConditionReason reason) {
        return switch (reason) {
            case TELEGRAM_LINKING -> "condition-tg-linking";
            case PLAYTIME -> "condition-playtime";
        };
    }

    public void forget(Player player) {
        lastWarningAt.remove(player.getUniqueId());
    }

    private boolean tryWarn(Player player) {
        long now = System.currentTimeMillis();
        long last = lastWarningAt.getOrDefault(player.getUniqueId(), 0L);
        if (now - last < warningCooldownMillis) {
            return false;
        }

        lastWarningAt.put(player.getUniqueId(), now);
        return true;
    }

    private TextComponent buildBoundaryBlockedMessage(Player player) {
        TextComponent root = new TextComponent(ChatColor.RED + get(player, "blocked") + " ");

        TextComponent register = new TextComponent(ChatColor.GREEN + get(player, "bind-button"));
        register.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tg account register"));
        register.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(get(player, "bind-hover"))));

        TextComponent spawn = new TextComponent(ChatColor.GREEN + " " + get(player, "spawn-button"));
        spawn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tgspawn"));
        spawn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(get(player, "spawn-hover"))));

        root.addExtra(register);
        root.addExtra(spawn);
        return root;
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
