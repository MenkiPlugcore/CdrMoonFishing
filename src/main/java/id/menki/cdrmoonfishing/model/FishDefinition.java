package id.menki.cdrmoonfishing.model;

import org.bukkit.Material;

import java.util.List;

public record FishDefinition(
        String id,
        String displayName,
        Material material,
        FishRarity rarity,
        FishBehavior behavior,
        double chance,
        double minWeight,
        double maxWeight,
        int minDepth,
        int maxDepth,
        double pullMin,
        double pullMax,
        List<String> biomes,
        List<String> weather,
        List<String> time,
        List<String> requiredBaits
) {
    public boolean matches(String biomeName, int depth, String weatherName, String timeName, String baitId) {
        if (depth < minDepth || depth > maxDepth) {
            return false;
        }

        boolean biomeOk = biomes.isEmpty()
                || biomes.contains("*")
                || biomes.stream().anyMatch(entry -> entry.equalsIgnoreCase(biomeName));

        boolean weatherOk = weather.isEmpty()
                || weather.stream().anyMatch(entry -> entry.equalsIgnoreCase("ANY") || entry.equalsIgnoreCase(weatherName));

        boolean timeOk = time.isEmpty()
                || time.stream().anyMatch(entry -> entry.equalsIgnoreCase("ANY") || entry.equalsIgnoreCase(timeName));

        boolean baitOk = requiredBaits.isEmpty()
                || (baitId != null && requiredBaits.stream().anyMatch(entry -> entry.equalsIgnoreCase(baitId)));

        return biomeOk && weatherOk && timeOk && baitOk;
    }
}
