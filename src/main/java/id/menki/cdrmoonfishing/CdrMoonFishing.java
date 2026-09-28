package id.menki.cdrmoonfishing;

import id.menki.cdrmoonfishing.bait.BaitManager;
import id.menki.cdrmoonfishing.command.FishDexCommand;
import id.menki.cdrmoonfishing.command.FishMarketCommand;
import id.menki.cdrmoonfishing.command.FishingCommand;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.fishing.FishingManager;
import id.menki.cdrmoonfishing.integration.IntegrationManager;
import id.menki.cdrmoonfishing.listener.FishMarketListener;
import id.menki.cdrmoonfishing.listener.FishingListener;
import id.menki.cdrmoonfishing.market.FishMarketManager;
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
    private VaultEconomyHook economyHook;
    private FishMarketManager fishMarketManager;

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
        this.economyHook = new VaultEconomyHook(this);
        this.economyHook.refresh();

        this.fishingManager = new FishingManager(this, fishRegistry, baitManager, playerStatsManager);
        this.fishMarketManager = new FishMarketManager(this, fishRegistry, economyHook);
        getServer().getPluginManager().registerEvents(new FishingListener(fishingManager, baitManager), this);
        getServer().getPluginManager().registerEvents(new FishMarketListener(fishMarketManager), this);

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

        FishMarketCommand marketCommand = new FishMarketCommand(fishMarketManager);
        PluginCommand fishMarket = getCommand("fishmarket");
        if (fishMarket != null) {
            fishMarket.setExecutor(marketCommand);
            fishMarket.setTabCompleter(marketCommand);
        } else {
            getLogger().severe("Command 'fishmarket' is missing from plugin.yml.");
        }

        getLogger().info("CdrMoonFishing v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Crossplay input mode: vanilla rod + standard chest market GUI + server-side tension.");
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
        if (economyHook != null) economyHook.refresh();
        getLogger().info("Configuration, fish registry, bait registry and economy hook reloaded.");
    }

    public FishingManager getFishingManager() { return fishingManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public BaitManager getBaitManager() { return baitManager; }
    public BaitRegistry getBaitRegistry() { return baitRegistry; }
    public FishRegistry getFishRegistry() { return fishRegistry; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; }
    public VaultEconomyHook getEconomyHook() { return economyHook; }
    public FishMarketManager getFishMarketManager() { return fishMarketManager; }
}
