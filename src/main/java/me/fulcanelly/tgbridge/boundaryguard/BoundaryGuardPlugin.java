package me.fulcanelly.tgbridge.boundaryguard;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.plugin.java.JavaPlugin;

import me.fulcanelly.tgbridge.Bridge;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.jobs.RestrictionRefreshJob;
import me.fulcanelly.tgbridge.boundaryguard.listeners.BoundaryMoveListener;
import me.fulcanelly.tgbridge.boundaryguard.listeners.RestrictionEventListener;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.Restriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.AdventureModeRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.ContainerUseRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.strategies.SpawnBoundaryRestriction;
import me.fulcanelly.tgbridge.boundaryguard.services.utils.PlayerWarningService;
import me.fulcanelly.tgbridge.boundaryguard.utils.MessageLocalizer;

public final class BoundaryGuardPlugin extends JavaPlugin {

    private static final String BRIDGE_PLUGIN_NAME = "tg-bridge";

    private BoundaryArea boundaryArea;
    private MessageLocalizer messages;
    private TelegramLinkStatusService telegramLinkStatus;
    private RestrictionService restrictions;
    private PlayerWarningService warnings;
    private BukkitTask refreshTask;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        messages = new MessageLocalizer(getConfig());

        if (getServer().getPluginManager().getPlugin(BRIDGE_PLUGIN_NAME) == null) {
            getLogger().severe("tg-bridge is required but was not found; disabling Boundary Guard.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Bridge bridge = (Bridge) getServer().getPluginManager().getPlugin(BRIDGE_PLUGIN_NAME);
        SignupLoginReception reception = bridge.getInjector().getInstance(SignupLoginReception.class);

        double allowedRadius = getConfig().getDouble("allowed-radius", 256.0);
        double spawnRadius = getConfig().getDouble("rules.strategies.keep-on-spawn.radius", allowedRadius);
        boundaryArea = new BoundaryArea(spawnRadius);
        messages = new MessageLocalizer(getConfig());

        long cooldownMillis = Math.round(getConfig().getDouble("message-cooldown-millis", 500.0));
        warnings = new PlayerWarningService(messages, cooldownMillis);
        long refreshPeriodTicks = Math.max(1L, getConfig().getLong("rules.telegram-check-interval-ticks", 60L));
        telegramLinkStatus = new TelegramLinkStatusService(reception, refreshPeriodTicks * 50L);

        Condition condition;
        try {
            condition = new ConditionFactory(telegramLinkStatus).fromConfig(getConfig().get("rules.condition"));
            restrictions = new RestrictionService(condition, buildRestrictions(allowedRadius));
        } catch (IllegalArgumentException exception) {
            getLogger().severe("Invalid rules configuration: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        getServer()
                .getPluginManager()
                .registerEvents(new BoundaryMoveListener(restrictions, warnings), this);
        getServer()
                .getPluginManager()
                .registerEvents(
                        new RestrictionEventListener(restrictions, telegramLinkStatus, warnings), this);

        refreshTask = getServer()
                .getScheduler()
                .runTaskTimer(this, new RestrictionRefreshJob(restrictions), refreshPeriodTicks, refreshPeriodTicks);

        if (getCommand("tgspawn") != null) {
            getCommand("tgspawn").setExecutor(this);
        }
    }

    private List<Restriction> buildRestrictions(double defaultRadius) {
        List<Restriction> configured = new ArrayList<>();
        String path = "rules.strategies";

        if (getConfig().isConfigurationSection(path + ".keep-on-spawn")) {
            double radius = getConfig().getDouble(path + ".keep-on-spawn.radius", defaultRadius);
            configured.add(new SpawnBoundaryRestriction(new BoundaryArea(radius)));
        }

        if (getConfig().isConfigurationSection(path + ".switch-2-adventure-mode")) {
            boolean everywhere = getConfig().getBoolean(path + ".switch-2-adventure-mode.everywhere", false);
            double radius = getConfig().getDouble(path + ".switch-2-adventure-mode.radius", defaultRadius);
            configured.add(new AdventureModeRestriction(new BoundaryArea(radius), everywhere));
        }

        if (getConfig().getBoolean(path + ".forbid-container-use", false)) {
            configured.add(new ContainerUseRestriction());
        }

        return configured;
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

    @Override
    public void onDisable() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        if (restrictions != null) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                restrictions.forget(player);
                telegramLinkStatus.forget(player);
            }
        }
    }
}
