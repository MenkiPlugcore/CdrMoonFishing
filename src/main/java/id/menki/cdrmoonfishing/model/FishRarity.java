package id.menki.cdrmoonfishing.model;

public enum FishRarity {
    COMMON("Common", 1.0),
    UNCOMMON("Uncommon", 1.15),
    RARE("Rare", 1.5),
    EPIC("Epic", 2.25),
    LEGENDARY("Legendary", 4.0);

    private final String displayName;
    private final double valueMultiplier;

    FishRarity(String displayName, double valueMultiplier) {
        this.displayName = displayName;
        this.valueMultiplier = valueMultiplier;
    }

    public String displayName() {
        return displayName;
    }

    public double valueMultiplier() {
        return valueMultiplier;
    }
}
