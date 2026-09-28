package id.menki.cdrmoonfishing.stats;

import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class PlayerStatsManager {
    private final JavaPlugin plugin;
    private final File playerDirectory;
    private final Map<UUID, YamlConfiguration> cache = new HashMap<>();
    private final Map<UUID, File> files = new HashMap<>();

    public PlayerStatsManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.playerDirectory = new File(plugin.getDataFolder(), "players");
        if (!playerDirectory.exists() && !playerDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create player statistics directory.");
        }
    }

    public CatchRecordResult recordCatch(Player player, FishDefinition fish, double weight) {
        UUID uuid = player.getUniqueId();
        YamlConfiguration yaml = profile(uuid);
        long now = System.currentTimeMillis();
        String fishPath = "fish." + fish.id();

        int previousCount = yaml.getInt(fishPath + ".count", 0);
        double previousSpeciesBest = yaml.getDouble(fishPath + ".best-weight", 0.0);
        double previousOverallBest = yaml.getDouble("stats.biggest.weight", 0.0);

        boolean newDiscovery = previousCount <= 0;
        boolean newSpeciesRecord = weight > previousSpeciesBest;
        boolean newOverallRecord = weight > previousOverallBest;

        yaml.set("player.name", player.getName());
        yaml.set("player.uuid", uuid.toString());
        yaml.set("player.last-updated", now);

        yaml.set("stats.total-catches", yaml.getInt("stats.total-catches", 0) + 1);
        yaml.set("stats.total-weight", yaml.getDouble("stats.total-weight", 0.0) + weight);
        if (fish.rarity() == FishRarity.LEGENDARY) {
            yaml.set("stats.legendary-catches", yaml.getInt("stats.legendary-catches", 0) + 1);
        }

        String rarityPath = "rarities." + fish.rarity().name();
        yaml.set(rarityPath, yaml.getInt(rarityPath, 0) + 1);

        yaml.set(fishPath + ".count", previousCount + 1);
        if (newDiscovery) {
            yaml.set(fishPath + ".first-caught-at", now);
        }
        yaml.set(fishPath + ".last-caught-at", now);
        yaml.set(fishPath + ".last-weight", weight);
        if (newSpeciesRecord) {
            yaml.set(fishPath + ".best-weight", weight);
        }

        if (newOverallRecord) {
            yaml.set("stats.biggest.fish-id", fish.id());
            yaml.set("stats.biggest.display-name", fish.displayName());
            yaml.set("stats.biggest.rarity", fish.rarity().name());
            yaml.set("stats.biggest.weight", weight);
            yaml.set("stats.biggest.caught-at", now);
        }

        save(uuid, yaml);
        return new CatchRecordResult(
                newDiscovery,
                newSpeciesRecord,
                newOverallRecord,
                countDiscovered(yaml),
                yaml.getInt("stats.total-catches", 0)
        );
    }

    public StatsSnapshot snapshot(Player player) {
        YamlConfiguration yaml = profile(player.getUniqueId());
        EnumMap<FishRarity, Integer> rarityCounts = new EnumMap<>(FishRarity.class);
        for (FishRarity rarity : FishRarity.values()) {
            rarityCounts.put(rarity, yaml.getInt("rarities." + rarity.name(), 0));
        }

        return new StatsSnapshot(
                yaml.getInt("stats.total-catches", 0),
                yaml.getDouble("stats.total-weight", 0.0),
                yaml.getInt("stats.legendary-catches", 0),
                countDiscovered(yaml),
                yaml.getString("stats.biggest.fish-id"),
                yaml.getString("stats.biggest.display-name"),
                yaml.getDouble("stats.biggest.weight", 0.0),
                Map.copyOf(rarityCounts)
        );
    }

    public FishDexEntry entry(Player player, String fishId) {
        YamlConfiguration yaml = profile(player.getUniqueId());
        String path = "fish." + fishId;
        int count = yaml.getInt(path + ".count", 0);
        return new FishDexEntry(
                count > 0,
                count,
                yaml.getDouble(path + ".best-weight", 0.0),
                yaml.getLong(path + ".first-caught-at", 0L),
                yaml.getLong(path + ".last-caught-at", 0L)
        );
    }

    public void reset(Player player) {
        UUID uuid = player.getUniqueId();
        cache.remove(uuid);
        File file = file(uuid);
        files.remove(uuid);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not reset fishing statistics for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public int cachedProfiles() {
        return cache.size();
    }

    public void shutdown() {
        for (Map.Entry<UUID, YamlConfiguration> entry : cache.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
        cache.clear();
        files.clear();
    }

    private YamlConfiguration profile(UUID uuid) {
        return cache.computeIfAbsent(uuid, ignored -> YamlConfiguration.loadConfiguration(file(uuid)));
    }

    private File file(UUID uuid) {
        return files.computeIfAbsent(uuid, ignored -> new File(playerDirectory, uuid + ".yml"));
    }

    private void save(UUID uuid, YamlConfiguration yaml) {
        try {
            yaml.save(file(uuid));
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save fishing statistics for " + uuid + ": " + ex.getMessage());
        }
    }

    private int countDiscovered(YamlConfiguration yaml) {
        ConfigurationSection fishSection = yaml.getConfigurationSection("fish");
        if (fishSection == null) {
            return 0;
        }

        int discovered = 0;
        for (String fishId : fishSection.getKeys(false)) {
            if (yaml.getInt("fish." + fishId + ".count", 0) > 0) {
                discovered++;
            }
        }
        return discovered;
    }

    public record CatchRecordResult(
            boolean newDiscovery,
            boolean newSpeciesRecord,
            boolean newOverallRecord,
            int discoveredSpecies,
            int totalCatches
    ) {
    }

    public record FishDexEntry(
            boolean discovered,
            int count,
            double bestWeight,
            long firstCaughtAt,
            long lastCaughtAt
    ) {
    }

    public record StatsSnapshot(
            int totalCatches,
            double totalWeight,
            int legendaryCatches,
            int discoveredSpecies,
            String biggestFishId,
            String biggestFishName,
            double biggestWeight,
            Map<FishRarity, Integer> rarityCounts
    ) {
    }
}
