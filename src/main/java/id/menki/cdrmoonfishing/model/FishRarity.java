package id.menki.cdrmoonfishing.model;

public enum FishRarity {
    COMMON("Umum", 1.0),
    UNCOMMON("Tidak Umum", 1.15),
    RARE("Langka", 1.5),
    EPIC("Epik", 2.25),
    LEGENDARY("Legendaris", 4.0);

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
