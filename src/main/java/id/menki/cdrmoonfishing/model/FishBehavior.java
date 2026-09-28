package id.menki.cdrmoonfishing.model;

public enum FishBehavior {
    CALM("Calm"),
    ERRATIC("Erratic"),
    AGGRESSIVE("Aggressive"),
    DIVING("Diving");

    private final String displayName;

    FishBehavior(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
