package id.menki.cdrmoonfishing.model;

import id.menki.cdrmoonfishing.item.FishItemDefinition;
import org.bukkit.Material;

import java.util.List;

public record FishDefinition(
        String id,
        String displayName,
        Material material,
        FishItemDefinition itemDefinition,
        FishRarity rarity,
        FishBehavior behavior,
        double chance,
        double minWeight,
        double maxWeight,
        int minDepth,
        int maxDepth,
        double pullMin,
        double pullMax,
        double basePricePerKg,
        boolean cookable,
        List<String> biomes,
        List<String> weather,
        List<String> time,
        List<String> requiredBaits,
        List<EncounterPhase> phases
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

    public EncounterPhase phaseAt(double progress) {
        EncounterPhase current = null;
        for (EncounterPhase phase : phases) {
            if (progress + 0.0001 < phase.startProgress()) {
                break;
            }
            current = phase;
        }
        return current;
    }
}
