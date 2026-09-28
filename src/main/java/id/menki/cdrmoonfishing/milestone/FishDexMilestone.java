package id.menki.cdrmoonfishing.milestone;

import id.menki.cdrmoonfishing.model.FishRarity;

import java.util.Locale;

public record FishDexMilestone(
        String id,
        String displayName,
        Type type,
        double targetPercent,
        FishRarity rarity,
        double rewardMoney,
        String rewardBaitId,
        int rewardBaitAmount,
        int rewardRodXp,
        double luckBonus
) {
    public enum Type {
        COLLECTION_PERCENT,
        FIRST_LEGENDARY,
        RARITY_COMPLETE;

        public static Type parse(String raw) {
            if (raw == null || raw.isBlank()) return COLLECTION_PERCENT;
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return COLLECTION_PERCENT;
            }
        }
    }
}
