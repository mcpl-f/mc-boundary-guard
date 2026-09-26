package me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionApplier;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionReason;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ReasonExpr;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionEffect;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import org.bukkit.entity.Player;

/**
 * Builds and sends localized Minecraft messages, including cooldown-limited
 * warnings.
 *
 * Every warning is assembled at send time from small, reusable locale pieces
 * instead of one pre-written sentence per scenario: a {@code reasons.*} entry
 * per {@link ConditionReason} (a label, and - only for a reason that's actually
 * something a player can run - a command and hover text, the same
 * click-plus-visible-command-text pattern the boundary-blocked message always
 * used for "bind Telegram"), a {@code restrictions.*} phrase naming what's
 * currently blocked, and {@code join.*} connector words: {@code and}/{@code or}
 * glue multiple reasons together (matching whether they came from an
 * {@code all} or {@code any} group), and {@code before}/{@code so-that} attach
 * the restriction - {@code before} for a reactive "you just got blocked"
 * message, {@code so-that} for a proactive reminder.
 *
 * English needs two different grammatical forms of a restriction phrase
 * depending on which connector precedes it - "before <gerund>" ("before
 * leaving the spawn area") vs. "so that you can <infinitive>" ("so that you
 * can leave the spawn area"; "so that you can leaving" is simply wrong - hence
 * {@code restrictions.<name>.gerund} and {@code .infinitive} are separate keys,
 * picked by {@link #restrictionKeyFor} based on which connector is calling it.
 * Russian doesn't need the distinction (its equivalent connectors both take a
 * bare infinitive), so both keys hold the same text there - the schema stays
 * uniform across locales even though only one locale actually needs two forms.
 *
 * Every part of the message gets its color from an explicit
 * {@link TextComponent#setColor}, never by embedding a {@link ChatColor}'s
 * legacy code into the text itself. The two look interchangeable in a test
 * that only ever reads the message back through {@code toLegacyText()} (which
 * resolves each node's *effective* color - inherited from its parent when the
 * node has none of its own - the same way whether that color came from
 * {@code setColor} or from a legacy code that happened to be the first thing
 * in the root's own text), but the plugin sends a JSON component tree, not a
 * legacy string: a client renders each node's own resolved color, and a raw
 * {@code §c} sitting inside one node's {@code text} does nothing for any
 * other node. A part that's meant to inherit red (the restriction phrase, a
 * plain-advice reason like playtime) needs the root's color to actually be
 * set structurally, or it renders in the client's default white - which is
 * exactly what was happening before this was written the explicit way.
 */
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

    /**
     * Sent when a movement attempt outside the area is blocked. Always appends
     * a {@code /tgspawn} escape hatch after the reason/restriction sentence -
     * unlike a {@code reasons.*} entry, this isn't a way to satisfy
     * {@code rules.condition} (walking back to spawn doesn't durably fix
     * anything the way linking Telegram does; the moment you try to leave
     * again you're blocked the same way), it's a separate, always-available
     * option for a player not ready to deal with verification right now.
     */
    public void sendBoundaryBlocked(Player player, Optional<ReasonExpr> reason) {
        if (reason.isEmpty() || !tryWarn(player)) {
            return;
        }

        TextComponent message = reasonMessage(
                player, reason.get(), "join.before", "gerund", List.of(RestrictionEffect.LEAVE_SPAWN));

        TextComponent spawn = new TextComponent(" " + get(player, "spawn-button"));
        spawn.setColor(ChatColor.BLUE);
        spawn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tgspawn"));
        spawn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(get(player, "spawn-hover"))));
        message.addExtra(spawn);

        player.spigot().sendMessage(message);
    }

    /** Sent when opening/using a container is blocked. */
    public void sendContainerBlocked(Player player, Optional<ReasonExpr> reason) {
        if (reason.isEmpty() || !tryWarn(player)) {
            return;
        }
        player.spigot().sendMessage(reasonMessage(
                player, reason.get(), "join.before", "gerund", List.of(RestrictionEffect.USE_CONTAINERS)));
    }

    /**
     * Reminds a restricted player what they still need to satisfy - e.g. right
     * after they're switched to Adventure Mode, when they try to interact with
     * the world while in it, or just periodically while idle. {@code reason}
     * comes from {@code RestrictionService#unmetReason}; empty sends nothing.
     *
     * {@code effects} is every {@link RestrictionEffect} worth mentioning here -
     * a single hardcoded one for an Adventure-Mode-specific trigger (that's the
     * only thing relevant to what the player just did), or
     * {@code RestrictionService#activeEffects()} for the idle-player job, which
     * isn't tied to any one action and should say everything currently active.
     */
    public void sendConditionReminder(Player player, Optional<ReasonExpr> reason, List<RestrictionEffect> effects) {
        if (reason.isEmpty() || effects.isEmpty() || !tryWarn(player)) {
            return;
        }
        player.spigot().sendMessage(reasonMessage(player, reason.get(), "join.so-that", "infinitive", effects));
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

    // Builds "<reasons> <joinKey> <effects>" as one red TextComponent tree, with
    // clickable reasons in blue, connectors and restrictions in yellow. This
    // has to be a component tree rather than one plain string precisely because
    // a reason can carry its own click/hover action. Red is set once, here, on
    // the root - every part below that doesn't set its own color (the join
    // words, the restriction phrase, a non-clickable reason) inherits it, so
    // there's nothing to keep reapplying as the tree is built up.
    private TextComponent reasonMessage(
            Player player, ReasonExpr reason, String joinKey, String restrictionForm, List<RestrictionEffect> effects) {
        ReasonExpr shownReason = effects.contains(RestrictionEffect.LEAVE_SPAWN)
                ? withoutReturnToSpawn(reason)
                : reason;

        TextComponent message = new TextComponent("");
        message.setColor(ChatColor.RED);
        message.addExtra(reasonExprComponent(player, shownReason));
        message.addExtra(coloredText(" " + get(player, joinKey) + " ", ChatColor.YELLOW));
        message.addExtra(effectsComponent(player, effects, restrictionForm));
        return message;
    }

    // RETURN_TO_SPAWN (see InSpawnRadiusCondition) is a fine alternative to
    // mention anywhere else, but naming it as the fix for the leave-spawn
    // restriction itself is circular - "return to spawn, before leaving the
    // spawn area" offers the one thing the player is currently failing to do
    // as though it solved that same problem. Dropping it collapses a Group
    // down to its other child, same as ConditionApplier would if that child
    // had never been unmet in the first place; if it was the *only* unmet
    // reason (an admin configured in-spawn-radius as the sole condition),
    // there's nothing non-circular left to say, so this falls back to the
    // original rather than showing nothing.
    private ReasonExpr withoutReturnToSpawn(ReasonExpr reason) {
        return dropReturnToSpawn(reason).orElse(reason);
    }

    private Optional<ReasonExpr> dropReturnToSpawn(ReasonExpr expr) {
        if (expr instanceof ReasonExpr.Leaf leaf) {
            return leaf.reason() == ConditionReason.RETURN_TO_SPAWN ? Optional.empty() : Optional.of(expr);
        }

        ReasonExpr.Group group = (ReasonExpr.Group) expr;
        List<ReasonExpr> children = group.children().stream()
                .map(this::dropReturnToSpawn)
                .flatMap(Optional::stream)
                .toList();

        if (children.isEmpty()) {
            return Optional.empty();
        }
        if (children.size() == 1) {
            return Optional.of(children.get(0));
        }
        return Optional.of(new ReasonExpr.Group(group.operator(), children));
    }

    // Multiple effects (only ever passed by the idle-player job, which isn't
    // tied to one action) are always joined with "and": they're all
    // simultaneously true, never alternatives the way reasons can be.
    private TextComponent effectsComponent(Player player, List<RestrictionEffect> effects, String form) {
        TextComponent joined = new TextComponent("");
        for (int i = 0; i < effects.size(); i++) {
            if (i > 0) {
                joined.addExtra(coloredText(" " + get(player, "join.and") + " ", ChatColor.YELLOW));
            }
            joined.addExtra(coloredText(get(player, restrictionKeyFor(effects.get(i), form)), ChatColor.YELLOW));
        }
        return joined;
    }

    private TextComponent reasonExprComponent(Player player, ReasonExpr expr) {
        if (expr instanceof ReasonExpr.Leaf leaf) {
            return reasonComponent(player, leaf.reason());
        }

        ReasonExpr.Group group = (ReasonExpr.Group) expr;
        String joinKey = group.operator() == ConditionApplier.Operator.ALL ? "join.and" : "join.or";
        List<ReasonExpr> children = group.children();

        TextComponent joined = new TextComponent("");
        for (int i = 0; i < children.size(); i++) {
            if (i > 0) {
                joined.addExtra(coloredText(" " + get(player, joinKey) + " ", ChatColor.YELLOW));
            }
            joined.addExtra(reasonExprComponent(player, children.get(i)));
        }
        return joined;
    }

    // A reason is clickable only if it has a runnable command (e.g. linking
    // Telegram, or returning to spawn) - the same click-plus-visible-command-text
    // pattern boundary-blocked always used, now the one place that pattern is
    // built. A reason that's just advice (e.g. "play longer") has no
    // command/hover to define, and stays plain text inheriting the surrounding
    // message's color.
    private TextComponent reasonComponent(Player player, ConditionReason reason) {
        String base = "reasons." + reasonKeyFor(reason);
        boolean clickable = has(player, base + ".command");

        TextComponent component = new TextComponent(get(player, base + ".label"));
        if (clickable) {
            component.setColor(ChatColor.BLUE);
            component.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, get(player, base + ".command")));
            component.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(get(player, base + ".hover"))));
        }
        return component;
    }

    private TextComponent coloredText(String text, ChatColor color) {
        TextComponent component = new TextComponent(text);
        component.setColor(color);
        return component;
    }

    // The only place that needs to know a ConditionReason maps to a messages.*
    // key - the conditions layer itself stays unaware config.yml even has a
    // messages section.
    private String reasonKeyFor(ConditionReason reason) {
        return switch (reason) {
            case TELEGRAM_LINKING -> "tg-linking";
            case PLAYTIME -> "playtime";
            case RETURN_TO_SPAWN -> "return-to-spawn";
        };
    }

    // Mirrors reasonKeyFor, for RestrictionEffect - the restrictions layer stays
    // unaware config.yml even has a messages section, same reasoning. `form` is
    // "gerund" (after bare "before") or "infinitive" (after "so that you can").
    private String restrictionKeyFor(RestrictionEffect effect, String form) {
        String name = switch (effect) {
            case LEAVE_SPAWN -> "leave-spawn";
            case USE_CONTAINERS -> "use-containers";
            case INTERACT_FREELY -> "interact-freely";
        };
        return "restrictions." + name + "." + form;
    }

    private boolean has(Player player, String key) {
        return config.hasMessage(playerLocale(player), key);
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
