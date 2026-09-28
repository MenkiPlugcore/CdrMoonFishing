package id.menki.cdrmoonfishing.model;

public record EncounterPhase(
        String id,
        String displayName,
        double startProgress,
        FishBehavior behavior,
        double pullMultiplier,
        double safeMinOffset,
        double safeMaxOffset,
        double reelPowerMultiplier,
        double progressMultiplier,
        String title,
        String subtitle,
        String sound
) {
}
