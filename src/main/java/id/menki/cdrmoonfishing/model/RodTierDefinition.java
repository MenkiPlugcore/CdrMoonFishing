package id.menki.cdrmoonfishing.model;

public record RodTierDefinition(
        String id,
        String displayName,
        int minXp,
        double reelMultiplier,
        double rarityLuck,
        double xpMultiplier
) {
}
