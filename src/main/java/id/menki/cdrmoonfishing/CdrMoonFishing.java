package id.menki.cdrmoonfishing;

import id.menki.cdrmoonfishing.command.FishingCommand;
import id.menki.cdrmoonfishing.fishing.FishingManager;
import id.menki.cdrmoonfishing.integration.IntegrationManager;
import id.menki.cdrmoonfishing.listener.FishingListener;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CdrMoonFishing extends JavaPlugin {
    private FishRegistry fishRegistry;
    private FishingManager fishingManager;
    private IntegrationManager integrationManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("fish.yml", false);

        this.fishRegistry = new FishRegistry(this);
        this.fishRegistry.reload();

        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.logStatus();

        this.fishingManager = new FishingManager(this, fishRegistry);
        getServer().getPluginManager().registerEvents(new FishingListener(fishingManager), this);

        FishingCommand fishingCommand = new FishingCommand(this);
        PluginCommand command = getCommand("fishing");
        if (command != null) {
            command.setExecutor(fishingCommand);
            command.setTabCompleter(fishingCommand);
        } else {
            getLogger().severe("Command 'fishing' is missing from plugin.yml.");
        }

        getLogger().info("CdrMoonFishing v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Crossplay input mode: vanilla fishing rod + server-side tension minigame.");
    }

    @Override
    public void onDisable() {
        if (fishingManager != null) {
            fishingManager.shutdown();
        }
    }

    public void reloadPlugin() {
        reloadConfig();
        fishRegistry.reload();
        getLogger().info("Configuration and fish registry reloaded.");
    }

    public FishingManager getFishingManager() {
        return fishingManager;
    }

    public IntegrationManager getIntegrationManager() {
        return integrationManager;
    }
}
