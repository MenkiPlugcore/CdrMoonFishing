package id.menki.cdrmoonfishing.tournament;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook.DepositResult;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class TournamentManager {
    private final CdrMoonFishing plugin;
    private final VaultEconomyHook economy;
    private final File stateFile;
    private final File historyFile;
    private final Map<UUID, Entry> entries = new LinkedHashMap<>();
    private final Map<UUID, PendingReward> pendingRewards = new LinkedHashMap<>();

    private boolean active;
    private TournamentMode mode = TournamentMode.POINTS;
    private long startedAt;
    private long endsAt;
    private BukkitTask ticker;
    private long lastPendingRetry;

    public TournamentManager(CdrMoonFishing plugin, VaultEconomyHook economy) {
        this.plugin = plugin;
        this.economy = economy;
        this.stateFile = new File(plugin.getDataFolder(), "tournament.yml");
        this.historyFile = new File(plugin.getDataFolder(), "tournament-history.yml");
        loadState();
        this.ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public synchronized boolean start(int minutes, TournamentMode newMode) {
        if (active) return false;
        long now = System.currentTimeMillis();
        this.active = true;
        this.mode = newMode == null ? TournamentMode.POINTS : newMode;
        this.startedAt = now;
        this.endsAt = now + Math.max(1, minutes) * 60_000L;
        this.entries.clear();
        saveState();

        broadcast(Component.text("✦ FISHING TOURNAMENT STARTED ✦", NamedTextColor.AQUA));
        broadcast(Component.text("Mode: " + mode.displayName() + " • Duration: " + Math.max(1, minutes) + " minutes", NamedTextColor.GRAY));
        broadcast(Component.text("Every successful CdrMoonFishing catch counts automatically.", NamedTextColor.DARK_GRAY));
        return true;
    }

    public synchronized void recordCatch(Player player, FishDefinition fish, double weight) {
        if (!active) return;
        long now = System.currentTimeMillis();
        if (now >= endsAt) {
            finish(true);
            return;
        }

        Entry entry = entries.computeIfAbsent(player.getUniqueId(), ignored -> new Entry(player.getUniqueId(), player.getName()));
        entry.name = player.getName();
        entry.catches++;
        entry.totalWeight += weight;
        entry.biggestWeight = Math.max(entry.biggestWeight, weight);
        entry.points += pointsFor(fish, weight);
        saveState();

        player.sendActionBar(Component.text("Tournament • " + mode.displayName() + ": " + formatScore(score(entry)), NamedTextColor.AQUA));
    }

    private void tick() {
        synchronized (this) {
            long now = System.currentTimeMillis();
            if (active && now >= endsAt) {
                finish(true);
            }
            if (now - lastPendingRetry >= 60_000L) {
                lastPendingRetry = now;
                retryPendingRewards();
            }
        }
    }

    public synchronized List<RankedEntry> ranking() {
        List<Entry> copy = new ArrayList<>(entries.values());
        Comparator<Entry> comparator = Comparator
                .comparingDouble((Entry entry) -> score(entry)).reversed()
                .thenComparing(Comparator.comparingDouble((Entry entry) -> entry.biggestWeight).reversed())
                .thenComparing(Comparator.comparingInt((Entry entry) -> entry.catches).reversed())
                .thenComparing(entry -> entry.name, String.CASE_INSENSITIVE_ORDER);
        copy.sort(comparator);

        List<RankedEntry> result = new ArrayList<>();
        for (int i = 0; i < copy.size(); i++) {
            Entry entry = copy.get(i);
            result.add(new RankedEntry(
                    i + 1,
                    entry.uuid,
                    entry.name,
                    score(entry),
                    entry.catches,
                    entry.totalWeight,
                    entry.biggestWeight,
                    entry.points
            ));
        }
        return List.copyOf(result);
    }

    public synchronized boolean stopWithRewards() {
        if (!active) return false;
        finish(true);
        return true;
    }

    public synchronized boolean cancel() {
        if (!active) return false;
        active = false;
        entries.clear();
        startedAt = 0L;
        endsAt = 0L;
        saveState();
        broadcast(Component.text("Fishing tournament cancelled. No rewards were paid.", NamedTextColor.RED));
        return true;
    }

    private void finish(boolean rewardWinners) {
        if (!active) return;
        List<RankedEntry> finalRanking = ranking();
        long finishedAt = System.currentTimeMillis();
        active = false;

        broadcast(Component.text("✦ FISHING TOURNAMENT FINISHED ✦", NamedTextColor.GOLD));
        if (finalRanking.isEmpty()) {
            broadcast(Component.text("No valid catches were recorded.", NamedTextColor.GRAY));
        } else {
            int shown = Math.min(3, finalRanking.size());
            for (int i = 0; i < shown; i++) {
                RankedEntry ranked = finalRanking.get(i);
                double reward = rewardForPlace(ranked.place());
                broadcast(Component.text("#" + ranked.place() + " " + ranked.name() + " • " + formatScore(ranked.score()),
                        ranked.place() == 1 ? NamedTextColor.YELLOW : NamedTextColor.AQUA));
                if (rewardWinners && reward > 0.0) {
                    payoutOrQueue(ranked.uuid(), ranked.name(), reward);
                }
            }
        }

        archive(finalRanking, finishedAt);
        entries.clear();
        startedAt = 0L;
        endsAt = 0L;
        saveState();
    }

    private void payoutOrQueue(UUID uuid, String name, double reward) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        DepositResult result = economy.deposit(player, reward);
        if (result.success()) {
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                online.sendMessage(Component.text("Tournament reward received: " + economy.format(reward), NamedTextColor.GREEN));
            }
            return;
        }

        PendingReward existing = pendingRewards.get(uuid);
        double combined = reward + (existing == null ? 0.0 : existing.amount());
        pendingRewards.put(uuid, new PendingReward(name, combined));
        plugin.getLogger().warning("Queued tournament reward for " + name + ": " + economy.format(combined)
                + " (" + result.error() + ")");
    }

    private void retryPendingRewards() {
        if (pendingRewards.isEmpty() || !economy.ensureReady()) return;
        boolean changed = false;
        for (Map.Entry<UUID, PendingReward> pending : new ArrayList<>(pendingRewards.entrySet())) {
            DepositResult result = economy.deposit(Bukkit.getOfflinePlayer(pending.getKey()), pending.getValue().amount());
            if (!result.success()) continue;

            Player online = Bukkit.getPlayer(pending.getKey());
            if (online != null) {
                online.sendMessage(Component.text("Pending tournament reward received: "
                        + economy.format(pending.getValue().amount()), NamedTextColor.GREEN));
            }
            pendingRewards.remove(pending.getKey());
            changed = true;
        }
        if (changed) saveState();
    }

    private double pointsFor(FishDefinition fish, double weight) {
        double rarity = plugin.getConfig().getDouble("tournament.points.rarity." + fish.rarity().name(), defaultRarityPoints(fish.rarity()));
        double weightMultiplier = plugin.getConfig().getDouble("tournament.points.weight-multiplier", 0.25);
        return Math.max(0.0, rarity + (weight * Math.max(0.0, weightMultiplier)));
    }

    private double defaultRarityPoints(FishRarity rarity) {
        return switch (rarity) {
            case COMMON -> 1.0;
            case UNCOMMON -> 3.0;
            case RARE -> 8.0;
            case EPIC -> 20.0;
            case LEGENDARY -> 60.0;
        };
    }

    private double score(Entry entry) {
        return switch (mode) {
            case POINTS -> entry.points;
            case TOTAL_WEIGHT -> entry.totalWeight;
            case BIGGEST -> entry.biggestWeight;
        };
    }

    public String formatScore(double value) {
        return switch (mode) {
            case POINTS -> String.format(Locale.US, "%.2f pts", value);
            case TOTAL_WEIGHT, BIGGEST -> String.format(Locale.US, "%.2f kg", value);
        };
    }

    private double rewardForPlace(int place) {
        if (!plugin.getConfig().getBoolean("tournament.rewards.enabled", true)) return 0.0;
        return switch (place) {
            case 1 -> plugin.getConfig().getDouble("tournament.rewards.first", 5000.0);
            case 2 -> plugin.getConfig().getDouble("tournament.rewards.second", 2500.0);
            case 3 -> plugin.getConfig().getDouble("tournament.rewards.third", 1000.0);
            default -> 0.0;
        };
    }

    private void archive(List<RankedEntry> ranking, long finishedAt) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(historyFile);
        String root = "history." + finishedAt;
        yaml.set(root + ".mode", mode.name());
        yaml.set(root + ".started-at", startedAt);
        yaml.set(root + ".finished-at", finishedAt);
        yaml.set(root + ".participants", ranking.size());
        for (RankedEntry ranked : ranking) {
            String path = root + ".ranking." + ranked.place();
            yaml.set(path + ".uuid", ranked.uuid().toString());
            yaml.set(path + ".name", ranked.name());
            yaml.set(path + ".score", ranked.score());
            yaml.set(path + ".catches", ranked.catches());
            yaml.set(path + ".total-weight", ranked.totalWeight());
            yaml.set(path + ".biggest-weight", ranked.biggestWeight());
            yaml.set(path + ".points", ranked.points());
            yaml.set(path + ".reward", rewardForPlace(ranked.place()));
        }
        try {
            yaml.save(historyFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save tournament history: " + ex.getMessage());
        }
    }

    private void loadState() {
        if (!stateFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        this.active = yaml.getBoolean("active", false);
        this.mode = TournamentMode.parse(yaml.getString("mode", "POINTS"));
        this.startedAt = yaml.getLong("started-at", 0L);
        this.endsAt = yaml.getLong("ends-at", 0L);

        ConfigurationSection entrySection = yaml.getConfigurationSection("entries");
        if (entrySection != null) {
            for (String rawUuid : entrySection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    String path = "entries." + rawUuid;
                    Entry entry = new Entry(uuid, yaml.getString(path + ".name", rawUuid.substring(0, 8)));
                    entry.catches = yaml.getInt(path + ".catches", 0);
                    entry.totalWeight = yaml.getDouble(path + ".total-weight", 0.0);
                    entry.biggestWeight = yaml.getDouble(path + ".biggest-weight", 0.0);
                    entry.points = yaml.getDouble(path + ".points", 0.0);
                    entries.put(uuid, entry);
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid tournament UUID: " + rawUuid);
                }
            }
        }

        ConfigurationSection pendingSection = yaml.getConfigurationSection("pending-rewards");
        if (pendingSection != null) {
            for (String rawUuid : pendingSection.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(rawUuid);
                    String path = "pending-rewards." + rawUuid;
                    pendingRewards.put(uuid, new PendingReward(
                            yaml.getString(path + ".name", rawUuid.substring(0, 8)),
                            yaml.getDouble(path + ".amount", 0.0)
                    ));
                } catch (IllegalArgumentException ignored) {
                    plugin.getLogger().warning("Ignoring invalid pending reward UUID: " + rawUuid);
                }
            }
        }
    }

    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", active);
        yaml.set("mode", mode.name());
        yaml.set("started-at", startedAt);
        yaml.set("ends-at", endsAt);
        for (Entry entry : entries.values()) {
            String path = "entries." + entry.uuid;
            yaml.set(path + ".name", entry.name);
            yaml.set(path + ".catches", entry.catches);
            yaml.set(path + ".total-weight", entry.totalWeight);
            yaml.set(path + ".biggest-weight", entry.biggestWeight);
            yaml.set(path + ".points", entry.points);
        }
        for (Map.Entry<UUID, PendingReward> pending : pendingRewards.entrySet()) {
            String path = "pending-rewards." + pending.getKey();
            yaml.set(path + ".name", pending.getValue().name());
            yaml.set(path + ".amount", pending.getValue().amount());
        }
        try {
            yaml.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save tournament state: " + ex.getMessage());
        }
    }

    private void broadcast(Component component) {
        plugin.getServer().getConsoleSender().sendMessage(component);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            player.sendMessage(component);
        }
    }

    public synchronized boolean isActive() { return active; }
    public synchronized TournamentMode mode() { return mode; }
    public synchronized long startedAt() { return startedAt; }
    public synchronized long endsAt() { return endsAt; }
    public synchronized long remainingMillis() { return active ? Math.max(0L, endsAt - System.currentTimeMillis()) : 0L; }
    public synchronized int participantCount() { return entries.size(); }
    public synchronized int pendingRewardCount() { return pendingRewards.size(); }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        synchronized (this) {
            saveState();
        }
    }

    private static final class Entry {
        private final UUID uuid;
        private String name;
        private int catches;
        private double totalWeight;
        private double biggestWeight;
        private double points;

        private Entry(UUID uuid, String name) {
            this.uuid = uuid;
            this.name = name;
        }
    }

    private record PendingReward(String name, double amount) {}

    public record RankedEntry(
            int place,
            UUID uuid,
            String name,
            double score,
            int catches,
            double totalWeight,
            double biggestWeight,
            double points
    ) {}
}
