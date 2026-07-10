package me.fulcanelly.tgbridge.boundaryguard;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;

import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import net.md_5.bungee.api.ChatColor;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.HoverEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.chat.hover.content.Text;

final class BoundaryMoveListener implements Listener {

    private final BoundaryArea boundaryArea;
    private final SignupLoginReception reception;
    private final MessageLocalizer messages;
    private final long messageCooldownMillis;
    private final Map<UUID, Long> lastMessageAt = new HashMap<>();

    BoundaryMoveListener(
            BoundaryArea boundaryArea,
            SignupLoginReception reception,
            MessageLocalizer messages,
            long messageCooldownMillis) {
        this.boundaryArea = boundaryArea;
        this.reception = reception;
        this.messages = messages;
        this.messageCooldownMillis = messageCooldownMillis;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {

        Location to = event.getTo();

        if (to == null || isSameBlock(event.getFrom(), to) || boundaryArea.contains(to)) {
            return;
        }

        Player player = event.getPlayer();

        if (isBound(player)) {
            return;
        }

        event.setTo(event.getFrom());
        warn(player);
    }

    private boolean isSameBlock(Location from, Location to) {
        return from.getWorld().equals(to.getWorld())
                && from.getBlockX() == to.getBlockX()
                && from.getBlockY() == to.getBlockY()
                && from.getBlockZ() == to.getBlockZ();
    }

    private boolean isBound(Player player) {
        return reception.getTgByUser(player.getName()).isPresent();
    }

    private void warn(Player player) {
        long now = System.currentTimeMillis();
        long last = lastMessageAt.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < messageCooldownMillis) {
            return;
        }

        lastMessageAt.put(player.getUniqueId(), now);
        player.spigot().sendMessage(buildBlockedMessage(player));
    }

    private TextComponent buildBlockedMessage(Player player) {
        TextComponent root = new TextComponent(ChatColor.RED + messages.get(player, "blocked") + " ");

        TextComponent register = new TextComponent(ChatColor.GREEN + messages.get(player, "bind-button"));
        register.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tg account register"));
        register.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(messages.get(player, "bind-hover"))));

        TextComponent spawn = new TextComponent(ChatColor.YELLOW + " " + messages.get(player, "spawn-button"));
        spawn.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/tgspawn"));
        spawn.setHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, new Text(messages.get(player, "spawn-hover"))));

        root.addExtra(register);
        root.addExtra(spawn);
        return root;
    }
}
