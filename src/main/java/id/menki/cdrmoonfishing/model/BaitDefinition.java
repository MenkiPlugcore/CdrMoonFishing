package id.menki.cdrmoonfishing.model;

import org.bukkit.Material;

import java.util.Locale;
import java.util.Map;

public record BaitDefinition(
        String id,
        String displayName,
        Material material,
        Map<FishRarity, Double> rarityMultipliers,
        Map<String, Double> fishMultipliers
) {
    public double multiplierFor(FishDefinition fish) {
        double rarityMultiplier = rarityMultipliers.getOrDefault(fish.rarity(), 1.0);
        double fishMultiplier = fishMultipliers.getOrDefault(fish.id().toLowerCase(Locale.ROOT), 1.0);
        return Math.max(0.0, rarityMultiplier * fishMultiplier);
    }
}
