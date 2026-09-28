package id.menki.cdrmoonfishing;

import id.menki.cdrmoonfishing.bait.BaitManager;
import id.menki.cdrmoonfishing.command.FishContractsCommand;
import id.menki.cdrmoonfishing.command.FishDexCommand;
import id.menki.cdrmoonfishing.command.FishDoctorCommand;
import id.menki.cdrmoonfishing.command.FishLeaderboardCommand;
import id.menki.cdrmoonfishing.command.FishMarketCommand;
import id.menki.cdrmoonfishing.command.FishMilestonesCommand;
import id.menki.cdrmoonfishing.command.FishRodCommand;
import id.menki.cdrmoonfishing.command.FishTournamentCommand;
import id.menki.cdrmoonfishing.command.FishingCommand;
import id.menki.cdrmoonfishing.command.FishingHubCommand;
import id.menki.cdrmoonfishing.contracts.ContractManager;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.fishing.FishingManager;
import id.menki.cdrmoonfishing.integration.IntegrationManager;
import id.menki.cdrmoonfishing.item.CatchIdentityManager;
import id.menki.cdrmoonfishing.item.CatchItemUpgradeManager;
import id.menki.cdrmoonfishing.item.FishItemProviderManager;
import id.menki.cdrmoonfishing.leaderboard.GlobalLeaderboardManager;
import id.menki.cdrmoonfishing.listener.FishMarketListener;
import id.menki.cdrmoonfishing.listener.FishingListener;
import id.menki.cdrmoonfishing.listener.FishingUiListener;
import id.menki.cdrmoonfishing.market.FishMarketManager;
import id.menki.cdrmoonfishing.milestone.FishDexMilestoneManager;
import id.menki.cdrmoonfishing.registry.BaitRegistry;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import id.menki.cdrmoonfishing.registry.RodRegistry;
import id.menki.cdrmoonfishing.rod.RodManager;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager;
import id.menki.cdrmoonfishing.tournament.TournamentManager;
import id.menki.cdrmoonfishing.ui.FishingUiManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class CdrMoonFishing extends JavaPlugin {
    private FishRegistry fishRegistry;
    private BaitRegistry baitRegistry;
    private RodRegistry rodRegistry;
    private BaitManager baitManager;
    private RodManager rodManager;
    private ContractManager contractManager;
    private FishDexMilestoneManager milestoneManager;
    private PlayerStatsManager playerStatsManager;
    private FishingManager fishingManager;
    private IntegrationManager integrationManager;
    private VaultEconomyHook economyHook;
    private FishMarketManager fishMarketManager;
    private TournamentManager tournamentManager;
    private GlobalLeaderboardManager globalLeaderboardManager;
    private FishItemProviderManager fishItemProviderManager;
    private CatchIdentityManager catchIdentityManager;
    private CatchItemUpgradeManager catchItemUpgradeManager;
    private FishingUiManager fishingUiManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        saveResource("fish.yml", false);
        saveResource("bait.yml", false);
        saveResource("rod.yml", false);
        saveResource("contracts.yml", false);
        saveResource("milestones.yml", false);

        this.fishRegistry = new FishRegistry(this);
        this.fishRegistry.reload();
        this.baitRegistry = new BaitRegistry(this);
        this.baitRegistry.reload();
        this.rodRegistry = new RodRegistry(this);
        this.rodRegistry.reload();
        this.baitManager = new BaitManager(baitRegistry);
        this.rodManager = new RodManager(this, rodRegistry);
        this.playerStatsManager = new PlayerStatsManager(this);

        this.integrationManager = new IntegrationManager(this);
        this.integrationManager.logStatus();
        this.economyHook = new VaultEconomyHook(this);
        this.economyHook.refresh();

        this.milestoneManager = new FishDexMilestoneManager(
                this, playerStatsManager, fishRegistry, baitRegistry, rodManager, economyHook);
        this.rodManager.setCollectionLuckProvider(milestoneManager::collectionLuck);
        this.contractManager = new ContractManager(this, economyHook, baitRegistry, rodManager);
        this.fishItemProviderManager = new FishItemProviderManager(this);
        this.catchIdentityManager = new CatchIdentityManager(this);
        this.catchItemUpgradeManager = new CatchItemUpgradeManager(this, fishItemProviderManager);

        this.tournamentManager = new TournamentManager(this, economyHook);
        this.playerStatsManager.registerCatchObserver(tournamentManager::recordCatch);
        this.playerStatsManager.registerCatchObserver(rodManager::recordCatch);
        this.playerStatsManager.registerCatchObserver(milestoneManager::recordCatch);
        this.playerStatsManager.registerCatchObserver(catchIdentityManager);
        this.playerStatsManager.registerCatchObserver(catchItemUpgradeManager);
        this.globalLeaderboardManager = new GlobalLeaderboardManager(this);

        this.fishingManager = new FishingManager(this, fishRegistry, baitManager, playerStatsManager, rodManager, contractManager);
        this.fishMarketManager = new FishMarketManager(this, fishRegistry, economyHook);
        this.fishingUiManager = new FishingUiManager(this);
        getServer().getPluginManager().registerEvents(new FishingListener(fishingManager, baitManager), this);
        getServer().getPluginManager().registerEvents(new FishMarketListener(fishMarketManager), this);
        getServer().getPluginManager().registerEvents(new FishingUiListener(fishingUiManager), this);

        PluginCommand cdrFish = getCommand("cdrfish");
        if (cdrFish != null) {
            cdrFish.setExecutor(new FishingHubCommand(fishingUiManager));
        } else getLogger().severe("Command 'cdrfish' is missing from plugin.yml.");

        FishingCommand fishingCommand = new FishingCommand(this);
        PluginCommand command = getCommand("fishing");
        if (command != null) {
            command.setExecutor(fishingCommand);
            command.setTabCompleter(fishingCommand);
        } else getLogger().severe("Command 'fishing' is missing from plugin.yml.");

        FishDexCommand fishDexCommand = new FishDexCommand(this);
        PluginCommand fishDex = getCommand("fishdex");
        if (fishDex != null) {
            fishDex.setExecutor(fishDexCommand);
            fishDex.setTabCompleter(fishDexCommand);
        } else getLogger().severe("Command 'fishdex' is missing from plugin.yml.");

        FishMarketCommand marketCommand = new FishMarketCommand(fishMarketManager);
        PluginCommand fishMarket = getCommand("fishmarket");
        if (fishMarket != null) {
            fishMarket.setExecutor(marketCommand);
            fishMarket.setTabCompleter(marketCommand);
        } else getLogger().severe("Command 'fishmarket' is missing from plugin.yml.");

        FishTournamentCommand tournamentCommand = new FishTournamentCommand(this, tournamentManager);
        PluginCommand fishTournament = getCommand("fishtournament");
        if (fishTournament != null) {
            fishTournament.setExecutor(tournamentCommand);
            fishTournament.setTabCompleter(tournamentCommand);
        } else getLogger().severe("Command 'fishtournament' is missing from plugin.yml.");

        FishLeaderboardCommand leaderboardCommand = new FishLeaderboardCommand(this, globalLeaderboardManager);
        PluginCommand fishLeaderboard = getCommand("fishleaderboard");
        if (fishLeaderboard != null) {
            fishLeaderboard.setExecutor(leaderboardCommand);
            fishLeaderboard.setTabCompleter(leaderboardCommand);
        } else getLogger().severe("Command 'fishleaderboard' is missing from plugin.yml.");

        FishRodCommand rodCommand = new FishRodCommand(rodManager);
        PluginCommand fishRod = getCommand("fishrod");
        if (fishRod != null) {
            fishRod.setExecutor(rodCommand);
            fishRod.setTabCompleter(rodCommand);
        } else getLogger().severe("Command 'fishrod' is missing from plugin.yml.");

        FishContractsCommand contractsCommand = new FishContractsCommand(contractManager);
        PluginCommand fishContracts = getCommand("fishcontracts");
        if (fishContracts != null) {
            fishContracts.setExecutor(contractsCommand);
            fishContracts.setTabCompleter(contractsCommand);
        } else getLogger().severe("Command 'fishcontracts' is missing from plugin.yml.");

        FishMilestonesCommand milestonesCommand = new FishMilestonesCommand(milestoneManager);
        PluginCommand fishMilestones = getCommand("fishmilestones");
        if (fishMilestones != null) {
            fishMilestones.setExecutor(milestonesCommand);
            fishMilestones.setTabCompleter(milestonesCommand);
        } else getLogger().severe("Command 'fishmilestones' is missing from plugin.yml.");

        PluginCommand fishDoctor = getCommand("fishdoctor");
        if (fishDoctor != null) {
            fishDoctor.setExecutor(new FishDoctorCommand(this));
        } else getLogger().severe("Command 'fishdoctor' is missing from plugin.yml.");

        getLogger().info("CdrMoonFishing v" + getPluginMeta().getVersion() + " enabled.");
        getLogger().info("Production UI: Fishing Hub + FishDex + global leaderboard + tournament chest GUIs enabled.");
        getLogger().info("Market security: unique catch identity + redemption ledger enabled.");
        getLogger().info("Fishing rod progression: " + rodRegistry.tiers().size() + " tiers loaded.");
        getLogger().info("FishDex milestones: " + milestoneManager.definitionCount() + " definitions loaded.");
        getLogger().info("Daily fishing contracts: " + contractManager.definitionCount() + " definitions loaded for "
                + contractManager.currentDate() + ".");
        getLogger().info("Tournament state: " + (tournamentManager.isActive() ? "ACTIVE" : "INACTIVE")
                + " | pending payouts: " + tournamentManager.pendingRewardCount());
    }

    @Override
    public void onDisable() {
        if (fishingManager != null) fishingManager.shutdown();
        if (tournamentManager != null) tournamentManager.shutdown();
        if (milestoneManager != null) milestoneManager.shutdown();
        if (playerStatsManager != null) playerStatsManager.shutdown();
    }

    public void reloadPlugin() {
        reloadConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();
        fishRegistry.reload();
        baitRegistry.reload();
        rodRegistry.reload();
        if (contractManager != null) contractManager.reload();
        if (milestoneManager != null) milestoneManager.reload();
        if (economyHook != null) economyHook.refresh();
        if (fishMarketManager != null) fishMarketManager.reloadSecurityLedger();
        if (fishItemProviderManager != null) fishItemProviderManager.clearWarnings();
        getLogger().info("Configuration and registries reloaded with missing defaults migrated.");
    }

    public FishingManager getFishingManager() { return fishingManager; }
    public IntegrationManager getIntegrationManager() { return integrationManager; }
    public BaitManager getBaitManager() { return baitManager; }
    public BaitRegistry getBaitRegistry() { return baitRegistry; }
    public FishRegistry getFishRegistry() { return fishRegistry; }
    public RodRegistry getRodRegistry() { return rodRegistry; }
    public RodManager getRodManager() { return rodManager; }
    public ContractManager getContractManager() { return contractManager; }
    public FishDexMilestoneManager getMilestoneManager() { return milestoneManager; }
    public PlayerStatsManager getPlayerStatsManager() { return playerStatsManager; }
    public VaultEconomyHook getEconomyHook() { return economyHook; }
    public FishMarketManager getFishMarketManager() { return fishMarketManager; }
    public TournamentManager getTournamentManager() { return tournamentManager; }
    public GlobalLeaderboardManager getGlobalLeaderboardManager() { return globalLeaderboardManager; }
    public FishItemProviderManager getFishItemProviderManager() { return fishItemProviderManager; }
    public FishingUiManager getFishingUiManager() { return fishingUiManager; }
}
