package id.menki.cdrmoonfishing;

import id.menki.cdrmoonfishing.bait.BaitManager;
import id.menki.cdrmoonfishing.command.FishDexCommand;
import id.menki.cdrmoonfishing.command.FishingCommand;
import id.menki.cdrmoonfishing.fishing.FishingManager;
import id.menki.cdrmoonfishing.integration.IntegrationManager;
import id.menki.cdrmoonfishing.listener.FishingListener;
import id.menki.cdrmoonfishing.registry.BaitRegistry;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CdrMoonFishing extends JavaPlugin {
    private FishRegistry fishRegistry;
    private BaitRegistry baitRegistry;
    private BaitManager baitManager;
    private PlayerStatsManager playerStatsManager;
    private FishingManager fishingManager;
    private IntegrationManager integrationManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("fish.yml", false);
        saveResource("bait.yml", false);

        this.fishRegistry = new FishRegistry(this);
        this.fishRegistry.reload();
        this.baitRegistry = new BaitRegistry(this);
        this.baitRegistry.reload();
        this.baitManager = new BaitManager(baitRegistry);
        this.playerStatsManager = new PlayerStatsManager(this);

        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.logStatus();

        this.fishingManager = new FishingManager(this, fishRegistry, baitManager, playerStatsManager);
        getServer().getPluginManager().registerEvents(new FishingListener(fishingManager, baitManager), this);

        FishingCommand fishingCommand = new FishingCommand(this);
        PluginCommand command = getCommand("fishing");
        if (command != null) {
            command.setExecutor(fishingCommand);
            command.setTabCompleter(fishingCommand);
        } else {
            getLogger().severe("Command 'fishing' is missing from plugin.yml.");
        }

        FishDexCommand fishDexCommand = new FishDexCommand(this);
        PluginCommand fishDex = getCommand("fishdex");
        if (fishDex != null) {
            fishDex.setExecutor(fishDexCommand);
            fishDex.setTabCompleter(fishDexCommand);
        } else {
            getLogger().severe("Command 'fishdex' is missing from plugin.yml.");
        }

        getLogger().info("CdrMoonFishing v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Crossplay input mode: vanilla rod + right-click bait + server-side tension.");
        getLogger().info("FishDex statistics storage: YAML per player UUID.");
    }

    @Override
    public void onDisable() {
        if (fishingManager != null) fishingManager.shutdown();
        if (playerStatsManager != null) playerStatsManager.shutdown();
    }

    public void reloadPlugin() {
        reloadConfig();
        fishRegistry.reload();
        baitRegistry.reload();
        getLogger().info("Configuration, fish registry and bait registry reloaded.");
    }

    public FishingManager getFishingManager() { return fishingManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public BaitManager getBaitManager() { return baitManager; }
    public BaitRegistry getBaitRegistry() { return baitRegistry; }
    public FishRegistry getFishRegistry() { return fishRegistry; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; }
}
