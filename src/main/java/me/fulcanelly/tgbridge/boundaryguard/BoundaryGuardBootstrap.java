package me.fulcanelly.tgbridge.boundaryguard;

import me.fulcanelly.tgbridge.Bridge;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.jobs.RestrictionRefreshJob;
import me.fulcanelly.tgbridge.boundaryguard.listeners.BoundaryMoveListener;
import me.fulcanelly.tgbridge.boundaryguard.listeners.RestrictionEventListener;
import me.fulcanelly.tgbridge.boundaryguard.listeners.commands.TgSpawnCommand;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Composes the plugin's services and performs startup registrations.
 *
 * Keeping construction and Bukkit side effects here leaves the JavaPlugin
 * entrypoint focused on the Bukkit lifecycle. This stays deliberately small
 * instead of introducing a general dependency container.
 */
@RequiredArgsConstructor
public final class BoundaryGuardBootstrap {

    private static final String BRIDGE_PLUGIN_NAME = "tg-bridge";

    private final BoundaryGuardPlugin plugin;

    public BoundaryGuardRuntime start() {
        Bridge bridge = findBridge();
        SignupLoginReception reception = bridge.getInjector().getInstance(SignupLoginReception.class);

        BoundaryGuardConfig config = new BoundaryGuardConfig(plugin.getConfig());
        MinecraftMessageService messages = new MinecraftMessageService(config, config.warningCooldownMillis());
        RestrictionFactory.Setup restrictionSetup = new RestrictionFactory(config).create();

        long refreshPeriodTicks = config.telegramCheckIntervalTicks();
        TelegramLinkStatusService telegramLinkStatus = new TelegramLinkStatusService(reception, refreshPeriodTicks * 50L);

        Condition condition = createCondition(telegramLinkStatus, config.conditionDefinition());
        RestrictionService restrictions = new RestrictionService(condition, restrictionSetup.restrictions());

        registerListeners(restrictions, telegramLinkStatus, messages);
        registerCommand(restrictionSetup.spawnArea(), messages);
        BukkitTask refreshTask = scheduleRefresh(restrictions, refreshPeriodTicks);

        return new BoundaryGuardRuntime(plugin, telegramLinkStatus, refreshTask);
    }

    private void registerListeners(
            RestrictionService restrictions,
            TelegramLinkStatusService telegramLinkStatus,
            MinecraftMessageService messages) {
        // Event registration is kept at the composition boundary; listeners only translate Bukkit events.
        plugin.getServer().getPluginManager().registerEvents(
                new BoundaryMoveListener(restrictions, messages),
                plugin);
        plugin.getServer().getPluginManager().registerEvents(
                new RestrictionEventListener(restrictions, telegramLinkStatus, messages),
                plugin);
    }

    private void registerCommand(BoundaryArea spawnArea, MinecraftMessageService messages) {
        var spawnCommand = plugin.getCommand("tgspawn");
        if (spawnCommand != null) {
            spawnCommand.setExecutor(new TgSpawnCommand(spawnArea, messages));
        } else {
            plugin.getLogger().warning("Command tgspawn is missing from plugin.yml");
        }
    }

    private BukkitTask scheduleRefresh(RestrictionService restrictions, long periodTicks) {
        return plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        new RestrictionRefreshJob(restrictions),
                        periodTicks,
                        periodTicks);
    }

    private Bridge findBridge() {
        Plugin dependency = plugin.getServer().getPluginManager().getPlugin(BRIDGE_PLUGIN_NAME);
        if (dependency instanceof Bridge bridge) {
            return bridge;
        }
        throw new IllegalStateException("tg-bridge is required but was not found; disabling Boundary Guard.");
    }

    private Condition createCondition(TelegramLinkStatusService telegramLinkStatus, Object conditionDefinition) {
        try {
            return new ConditionFactory(telegramLinkStatus).fromConfig(conditionDefinition);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("Invalid rules configuration: " + exception.getMessage(), exception);
        }
    }
}
