package id.menki.cdrmoonfishing.model;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Relative catch size based on a fish species' configured min/max weight.
 *
 * This is intentionally relative per species so a 15 kg eel can be GIANT while
 * the same absolute weight can still be small for a leviathan. Model assets can
 * later map directly to the stored id without changing catch logic.
 */
public enum FishSizeClass {
    SMALL("small", "Kecil", 0.65, NamedTextColor.GRAY),
    NORMAL("normal", "Normal", 0.85, NamedTextColor.WHITE),
    LARGE("large", "Besar", 1.10, NamedTextColor.AQUA),
    GIANT("giant", "Raksasa", 1.35, NamedTextColor.LIGHT_PURPLE),
    TROPHY("trophy", "TROFI ✦", 1.60, NamedTextColor.GOLD);

    private final String id;
    private final String displayName;
    private final double heldScale;
    private final NamedTextColor color;

    FishSizeClass(String id, String displayName, double heldScale, NamedTextColor color) {
        this.id = id;
        this.displayName = displayName;
        this.heldScale = heldScale;
        this.color = color;
    }

    public String id() { return id; }
    public String displayName() { return displayName; }
    public double heldScale() { return heldScale; }
    public NamedTextColor color() { return color; }

    public static FishSizeClass fromWeight(FishDefinition fish, double weight) {
        double range = fish.maxWeight() - fish.minWeight();
        if (range <= 0.000001) return NORMAL;

        double ratio = (weight - fish.minWeight()) / range;
        ratio = Math.max(0.0, Math.min(1.0, ratio));

        if (ratio < 0.25) return SMALL;
        if (ratio < 0.55) return NORMAL;
        if (ratio < 0.80) return LARGE;
        if (ratio < 0.95) return GIANT;
        return TROPHY;
    }

    public static FishSizeClass parse(String raw) {
        if (raw == null) return NORMAL;
        for (FishSizeClass value : values()) {
            if (value.id.equalsIgnoreCase(raw) || value.name().equalsIgnoreCase(raw)) return value;
        }
        return NORMAL;
    }
}
