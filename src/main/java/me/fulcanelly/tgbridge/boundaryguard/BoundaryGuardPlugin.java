package me.fulcanelly.tgbridge.boundaryguard;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import me.fulcanelly.tgbridge.Bridge;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;

public final class BoundaryGuardPlugin extends JavaPlugin implements CommandExecutor {

    private static final String BRIDGE_PLUGIN_NAME = "tg-bridge";

    private BoundaryArea boundaryArea;
    private MessageLocalizer messages;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageLocalizer(getConfig());

        Bridge bridge = (Bridge) getServer().getPluginManager().getPlugin(BRIDGE_PLUGIN_NAME);
        SignupLoginReception reception = bridge.getInjector().getInstance(SignupLoginReception.class);

        boundaryArea = new BoundaryArea(getConfig().getDouble("allowed-radius", 256.0));

        long cooldownMillis = Math.round(getConfig().getDouble("message-cooldown-millis", 500.0));

        getServer()
                .getPluginManager()
                .registerEvents(
                        new BoundaryMoveListener(boundaryArea, reception, messages, cooldownMillis), this);
        getCommand("tgspawn").setExecutor(this);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(messages.getDefault("only-player"));
            return true;
        }

        Player player = (Player) sender;

        if (boundaryArea.contains(player.getLocation())) {
            player.sendMessage(messages.get(player, "already-in-area"));
            return true;
        }

        player.teleport(boundaryArea.getTeleportLocation(player.getWorld()));
        player.sendMessage(ChatColor.GOLD + messages.get(player, "teleported"));
        return true;
    }
}
