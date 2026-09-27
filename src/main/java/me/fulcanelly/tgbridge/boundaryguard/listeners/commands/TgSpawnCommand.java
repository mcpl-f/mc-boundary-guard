package me.fulcanelly.tgbridge.boundaryguard.listeners.commands;

import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;

import net.md_5.bungee.api.ChatColor;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@RequiredArgsConstructor
public final class TgSpawnCommand implements CommandExecutor {

    private final BoundaryArea boundaryArea;
    private final MinecraftMessageService messages;

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + messages.getDefault("tgspawn-command.only-player"));
            return true;
        }

        if (boundaryArea.contains(player.getLocation())) {
            player.sendMessage(ChatColor.GREEN + messages.get(player, "tgspawn-command.already-in-area"));
            return true;
        }

        player.teleport(boundaryArea.getTeleportLocation(player.getWorld()));
        player.sendMessage(ChatColor.GREEN + messages.get(player, "tgspawn-command.teleported"));
        return true;
    }
}
