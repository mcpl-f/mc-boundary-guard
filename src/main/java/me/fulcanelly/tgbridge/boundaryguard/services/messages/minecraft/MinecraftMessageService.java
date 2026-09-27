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
 * {@link TextComponent#setColor} ({@link #coloredText}, or directly on a
 * component built elsewhere), never by embedding a {@link ChatColor}'s legacy
 * code into the text itself. The two look interchangeable in a test that only
 * ever reads the message back through {@code toLegacyText()} (which resolves
 * each node's *effective* color - inherited from its parent when the node has
 * none of its own - the same way whether that color came from {@code
 * setColor} or from a legacy code that happened to be the first thing in the
 * root's own text), but the plugin sends a JSON component tree, not a legacy
 * string: a client renders each node's own resolved color, and a raw
 * {@code §c} sitting inside one node's {@code text} does nothing for any
 * other node. Red is the root's color (a plain-advice reason like playtime
 * genuinely inherits it, having no color of its own); a clickable reason is
 * blue and a join word/restriction phrase is yellow, both set explicitly
 * rather than relied on to inherit - either way, something that's never
 * given a real structural color renders in the client's default white, which
 * is exactly what was happening before this was written the explicit way.
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


    /**
     * Sent when a movement attempt outside the area is blocked. This always
     * describes the {@code leave-spawn} restriction, and that's the one
     * restriction {@code reasons.return-to-spawn} can't be offered as a fix
     * for, so it's always appended afterward instead, as its own separate
     * action - regardless of whether it also happens to be part of
     * {@code reason} (rare: only when the player was already outside the area
     * when this got triggered).
     */
    public void sendBoundaryBlocked(Player player, Optional<ReasonExpr> reason) {
        if (reason.isEmpty() || !tryWarn(player)) {
            return;
        }

        TextComponent message = reasonMessage(
                player, reason.get(), "join.before", "gerund", List.of(RestrictionEffect.LEAVE_SPAWN));
        message.addExtra(" ");
        message.addExtra(reasonComponent(player, ConditionReason.RETURN_TO_SPAWN));

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
        TextComponent message = new TextComponent("");
        message.setColor(ChatColor.BLUE);
        message.addExtra(reasonExprComponent(player, reason));
        message.addExtra(coloredText(" " + get(player, joinKey) + " ", ChatColor.YELLOW));
        message.addExtra(effectsComponent(player, effects, restrictionForm));
        return message;
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
        // Whether a reason is actionable at all is decided once, from the
        // default locale's config, not per player - so a translator who forgot
        // to carry a command/hover into one locale doesn't make the same
        // reason clickable for some players and plain text for others.
        boolean clickable = has(base + ".command");

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
            case DISCORD_LINKING -> "ds-linking";
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

    private boolean has(String key) {
        return config.hasMessage(defaultLocale, key);
    }

    public String get(Player player, String key) {
        return config.message(playerLocale(player), key);
    }

    public String getDefault(String key) {
        return config.message(defaultLocale, key);
    }

    private String playerLocale(Player player) {
        String locale = player.getLocale();
        if (locale == null || locale.isEmpty()) {
            return defaultLocale;
        }
        return normalizeLocale(locale);
    }

    private String normalizeLocale(String locale) {
        return locale.toLowerCase().split("[_-]", 2)[0];
    }
}
