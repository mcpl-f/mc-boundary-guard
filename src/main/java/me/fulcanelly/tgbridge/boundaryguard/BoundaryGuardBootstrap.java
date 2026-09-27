package me.fulcanelly.tgbridge.boundaryguard;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import me.fulcanelly.tgbridge.Bridge;
import me.fulcanelly.tgbridge.tools.twofactor.register.SignupLoginReception;
import lombok.RequiredArgsConstructor;
import me.fulcanelly.tgbridge.boundaryguard.config.BoundaryGuardConfig;
import me.fulcanelly.tgbridge.boundaryguard.domain.BoundaryArea;
import me.fulcanelly.tgbridge.boundaryguard.integrations.tgbridge.TelegramLinkStatusService;
import me.fulcanelly.tgbridge.boundaryguard.jobs.ConditionReminderJob;
import me.fulcanelly.tgbridge.boundaryguard.jobs.RestrictionRefreshJob;
import me.fulcanelly.tgbridge.boundaryguard.listeners.BoundaryMoveListener;
import me.fulcanelly.tgbridge.boundaryguard.listeners.RestrictionEventListener;
import me.fulcanelly.tgbridge.boundaryguard.listeners.commands.TgSpawnCommand;
import me.fulcanelly.tgbridge.boundaryguard.services.messages.minecraft.MinecraftMessageService;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.Condition;
import me.fulcanelly.tgbridge.boundaryguard.services.conditions.ConditionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionFactory;
import me.fulcanelly.tgbridge.boundaryguard.services.restrictions.RestrictionService;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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

    // Shipped with the jar (see src/main/resources/lang) and always saved to
    // the data folder on first run, same as config.yml. A lang/*.yml an admin
    // drops in beyond these is still picked up by loadLocales() below - this
    // list only guarantees the two shipped translations exist on disk.
    private static final List<String> SHIPPED_LOCALES = List.of("en", "ru");

    private final BoundaryGuardPlugin plugin;

    public BoundaryGuardRuntime start() {
        Bridge bridge = findBridge();
        SignupLoginReception reception = bridge.getInjector().getInstance(SignupLoginReception.class);

        BoundaryGuardConfig config = new BoundaryGuardConfig(plugin.getConfig(), loadLocales());
        MinecraftMessageService messages = new MinecraftMessageService(config, config.warningCooldownMillis());
        RestrictionFactory.Setup restrictionSetup = new RestrictionFactory(config).create();

        long refreshPeriodTicks = config.restrictionRefreshIntervalTicks();
        TelegramLinkStatusService telegramLinkStatus =
                new TelegramLinkStatusService(reception, config.telegramRecheckCooldownMillis());

        Condition condition = createCondition(telegramLinkStatus, config.conditionDefinition());
        RestrictionService restrictions = new RestrictionService(condition, restrictionSetup.restrictions());

        registerListeners(restrictions, telegramLinkStatus, messages);
        registerCommand(restrictionSetup.spawnArea(), messages);
        BukkitTask refreshTask = scheduleRefresh(restrictions, refreshPeriodTicks);
        Optional<BukkitTask> conditionReminderTask = config.conditionReminderEnabled()
                ? Optional.of(scheduleConditionReminder(restrictions, messages, config.conditionReminderIntervalTicks()))
                : Optional.empty();

        return new BoundaryGuardRuntime(refreshTask, conditionReminderTask);
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

    private BukkitTask scheduleConditionReminder(
            RestrictionService restrictions, MinecraftMessageService messages, long periodTicks) {
        return plugin.getServer()
                .getScheduler()
                .runTaskTimer(
                        plugin,
                        new ConditionReminderJob(restrictions, messages),
                        periodTicks,
                        periodTicks);
    }

    // Ensures every shipped lang/*.yml exists in the data folder, then reads
    // whatever's actually there - so a translation an admin drops in (or edits)
    // is picked up without a code change, the same way config.yml itself works.
    private Map<String, ConfigurationSection> loadLocales() {
        for (String locale : SHIPPED_LOCALES) {
            plugin.saveResource("lang/" + locale + ".yml", false);
        }

        File langDir = new File(plugin.getDataFolder(), "lang");
        File[] files = langDir.listFiles((dir, name) -> name.endsWith(".yml"));

        Map<String, ConfigurationSection> locales = new LinkedHashMap<>();
        if (files != null) {
            for (File file : files) {
                String locale = file.getName().substring(0, file.getName().length() - ".yml".length());
                locales.put(locale, YamlConfiguration.loadConfiguration(file));
            }
        }
        return locales;
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
