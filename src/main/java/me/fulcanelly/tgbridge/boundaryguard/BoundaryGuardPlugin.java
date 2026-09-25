package me.fulcanelly.tgbridge.boundaryguard;

import org.bukkit.plugin.java.JavaPlugin;

public final class BoundaryGuardPlugin extends JavaPlugin {

    private BoundaryGuardRuntime runtime;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        try {
            runtime = new BoundaryGuardBootstrap(this).start();
        } catch (IllegalStateException exception) {
            getLogger().severe(exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {
        if (runtime != null) {
            runtime.stop();
            runtime = null;
        }
    }
}
