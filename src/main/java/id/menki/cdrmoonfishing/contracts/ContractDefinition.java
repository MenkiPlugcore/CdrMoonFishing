package id.menki.cdrmoonfishing.contracts;

import id.menki.cdrmoonfishing.fishing.FishingSession;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;

public record ContractDefinition(
        String id,
        String displayName,
        String description,
        ProgressMode progressMode,
        double target,
        String fishId,
        FishRarity minRarity,
        String baitId,
        int minDepth,
        int maxDepth,
        String time,
        String weather,
        double rewardMoney,
        String rewardBaitId,
        int rewardBaitAmount,
        int rewardRodXp
) {
    public boolean matches(FishDefinition fish, FishingSession session) {
        if (fishId != null && !fish.id().equalsIgnoreCase(fishId)) return false;
        if (minRarity != null && fish.rarity().ordinal() < minRarity.ordinal()) return false;
        if (baitId != null && (session.baitId() == null || !session.baitId().equalsIgnoreCase(baitId))) return false;
        if (minDepth >= 0 && session.depth() < minDepth) return false;
        if (maxDepth >= 0 && session.depth() > maxDepth) return false;
        if (time != null && !time.equalsIgnoreCase(session.time())) return false;
        if (weather != null && !weather.equalsIgnoreCase(session.weather())) return false;
        return true;
    }

    public double progressAmount(double weight) {
        return progressMode == ProgressMode.WEIGHT ? Math.max(0.0, weight) : 1.0;
    }

    public enum ProgressMode {
        COUNT,
        WEIGHT;

        public static ProgressMode parse(String raw) {
            if (raw == null) return COUNT;
            try {
                return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
            } catch (IllegalArgumentException ignored) {
                return COUNT;
            }
        }
    }
}
