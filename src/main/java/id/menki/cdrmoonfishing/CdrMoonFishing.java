package id.menki.cdrmoonfishing;

import id.menki.cdrmoonfishing.bait.BaitManager;
import id.menki.cdrmoonfishing.command.FishDexCommand;
import id.menki.cdrmoonfishing.command.FishLeaderboardCommand;
import id.menki.cdrmoonfishing.command.FishMarketCommand;
import id.menki.cdrmoonfishing.command.FishTournamentCommand;
import id.menki.cdrmoonfishing.command.FishingCommand;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.fishing.FishingManager;
import id.menki.cdrmoonfishing.integration.IntegrationManager;
import id.menki.cdrmoonfishing.item.CatchItemUpgradeManager;
import id.menki.cdrmoonfishing.item.FishItemProviderManager;
import id.menki.cdrmoonfishing.leaderboard.GlobalLeaderboardManager;
import id.menki.cdrmoonfishing.listener.FishMarketListener;
import id.menki.cdrmoonfishing.listener.FishingListener;
import id.menki.cdrmoonfishing.market.FishMarketManager;
import id.menki.cdrmoonfishing.registry.BaitRegistry;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager;
import id.menki.cdrmoonfishing.tournament.TournamentManager;
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
    private TournamentManager tournamentManager;
    private GlobalLeaderboardManager globalLeaderboardManager;
    private FishItemProviderManager fishItemProviderManager;
    private CatchItemUpgradeManager catchItemUpgradeManager;

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

        this.fishItemProviderManager = new FishItemProviderManager(this);
        this.catchItemUpgradeManager = new CatchItemUpgradeManager(this, fishItemProviderManager);

        this.tournamentManager = new TournamentManager(this, economyHook);
        this.playerStatsManager.registerCatchObserver(tournamentManager::recordCatch);
        this.playerStatsManager.registerCatchObserver(catchItemUpgradeManager);
        this.globalLeaderboardManager = new GlobalLeaderboardManager(this);

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

        FishTournamentCommand tournamentCommand = new FishTournamentCommand(this, tournamentManager);
        PluginCommand fishTournament = getCommand("fishtournament");
        if (fishTournament != null) {
            fishTournament.setExecutor(tournamentCommand);
            fishTournament.setTabCompleter(tournamentCommand);
        } else {
            getLogger().severe("Command 'fishtournament' is missing from plugin.yml.");
        }

        FishLeaderboardCommand leaderboardCommand = new FishLeaderboardCommand(globalLeaderboardManager);
        PluginCommand fishLeaderboard = getCommand("fishleaderboard");
        if (fishLeaderboard != null) {
            fishLeaderboard.setExecutor(leaderboardCommand);
            fishLeaderboard.setTabCompleter(leaderboardCommand);
        } else {
            getLogger().severe("Command 'fishleaderboard' is missing from plugin.yml.");
        }

        getLogger().info("CdrMoonFishing v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Crossplay input mode: vanilla rod + standard chest market GUI + server-side tension.");
        getLogger().info("FishDex statistics storage: YAML per player UUID.");
        getLogger().info("Custom fish items: VANILLA / ItemsAdder / MMOItems / AUTO with vanilla fallback.");
        getLogger().info("Tournament state: " + (tournamentManager.isActive() ? "ACTIVE" : "INACTIVE")
                + " | pending payouts: " + tournamentManager.pendingRewardCount());
    }

    @Override
    public void onDisable() {
        if (fishingManager != null) fishingManager.shutdown();
        if (tournamentManager != null) tournamentManager.shutdown();
        if (playerStatsManager != null) playerStatsManager.shutdown();
    }

    public void reloadPlugin() {
        reloadConfig();
        fishRegistry.reload();
        baitRegistry.reload();
        if (economyHook != null) economyHook.refresh();
        if (fishItemProviderManager != null) fishItemProviderManager.clearWarnings();
        getLogger().info("Configuration, fish registry, bait registry, economy hook and item providers reloaded.");
    }

    public FishingManager getFishingManager() { return fishingManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public BaitManager getBaitManager() { return baitManager; }
    public BaitRegistry getBaitRegistry() { return baitRegistry; }
    public FishRegistry getFishRegistry() { return fishRegistry; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; }
    public VaultEconomyHook getEconomyHook() { return economyHook; }
    public FishMarketManager getFishMarketManager() { return fishMarketManager; }
    public TournamentManager getTournamentManager() { return tournamentManager; }
    public GlobalLeaderboardManager getGlobalLeaderboardManager() { return globalLeaderboardManager; }
    public FishItemProviderManager getFishItemProviderManager() { return fishItemProviderManager; }
}
