package id.menki.cdrmoonfishing.leaderboard;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class GlobalLeaderboardManager {
    private final File playerDirectory;

    public GlobalLeaderboardManager(JavaPlugin plugin) {
        this.playerDirectory = new File(plugin.getDataFolder(), "players");
    }

    public List<Entry> ranking(LeaderboardMetric metric) {
        if (!playerDirectory.exists() || !playerDirectory.isDirectory()) return List.of();
        File[] files = playerDirectory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null || files.length == 0) return List.of();

        List<Entry> entries = new ArrayList<>();
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            String rawUuid = yaml.getString("player.uuid");
            UUID uuid;
            try {
                uuid = rawUuid == null ? UUID.fromString(file.getName().replace(".yml", "")) : UUID.fromString(rawUuid);
            } catch (IllegalArgumentException ex) {
                continue;
            }

            String name = yaml.getString("player.name", uuid.toString().substring(0, 8));
            int catches = yaml.getInt("stats.total-catches", 0);
            double weight = yaml.getDouble("stats.total-weight", 0.0);
            double biggest = yaml.getDouble("stats.biggest.weight", 0.0);
            int legendary = yaml.getInt("stats.legendary-catches", 0);
            if (catches <= 0 && weight <= 0.0 && biggest <= 0.0 && legendary <= 0) continue;

            entries.add(new Entry(uuid, name, catches, weight, biggest, legendary));
        }

        Comparator<Entry> comparator = switch (metric) {
            case CATCHES -> Comparator.comparingInt(Entry::totalCatches).reversed();
            case WEIGHT -> Comparator.comparingDouble(Entry::totalWeight).reversed();
            case BIGGEST -> Comparator.comparingDouble(Entry::biggestWeight).reversed();
            case LEGENDARY -> Comparator.comparingInt(Entry::legendaryCatches).reversed();
        };
        comparator = comparator.thenComparing(Entry::name, String.CASE_INSENSITIVE_ORDER);
        entries.sort(comparator);
        return List.copyOf(entries);
    }

    public String formatValue(LeaderboardMetric metric, Entry entry) {
        return switch (metric) {
            case CATCHES -> entry.totalCatches() + " catches";
            case WEIGHT -> String.format(Locale.US, "%.2f kg", entry.totalWeight());
            case BIGGEST -> String.format(Locale.US, "%.2f kg", entry.biggestWeight());
            case LEGENDARY -> entry.legendaryCatches() + " legendary";
        };
    }

    public record Entry(
            UUID uuid,
            String name,
            int totalCatches,
            double totalWeight,
            double biggestWeight,
            int legendaryCatches
    ) {}
}
