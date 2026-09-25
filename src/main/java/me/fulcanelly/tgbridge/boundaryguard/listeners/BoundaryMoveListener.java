package me.fulcanelly.tgbridge.boundaryguard.listeners;

import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;
import me.fulcanelly.tgbridge.boundaryguard.services.utils.PlayerWarningService;

import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

public final class BoundaryMoveListener implements Listener {

    private final RestrictionService restrictions;
    private final PlayerWarningService warnings;

    public BoundaryMoveListener(
            RestrictionService restrictions,
            PlayerWarningService warnings) {
        this.restrictions = restrictions;
        this.warnings = warnings;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || isSameBlock(event.getFrom(), to)) {
            return;
        }

        Player player = event.getPlayer();
        if (restrictions.allowsMovement(player, to)) {
            return;
        }

        event.setTo(event.getFrom());
        if (warnings.tryWarn(player)) {
            player.spigot().sendMessage(buildBlockedMessage(player));
        }
    }

    private boolean isSameBlock(Location from, Location to) {
        return from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    private TextComponent buildBlockedMessage(Player player) {
        TextComponent root = new TextComponent(ChatColor.RED + warnings.message(player, "blocked") + " ");

        TextComponent register = new TextComponent(ChatColor.GREEN + warnings.message(player, "bind-button"));
        register.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tg account register"));
        register.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(warnings.message(player, "bind-hover"))));

        TextComponent spawn = new TextComponent(ChatColor.YELLOW + " " + warnings.message(player, "spawn-button"));
        spawn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tgspawn"));
        spawn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(warnings.message(player, "spawn-hover"))));

        root.addExtra(register);
        root.addExtra(spawn);
        return root;
    }
}
