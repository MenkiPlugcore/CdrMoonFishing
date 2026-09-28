package id.menki.cdrmoonfishing.model;

public enum FishBehavior {
    CALM("Tenang"),
    ERRATIC("Liar"),
    AGGRESSIVE("Agresif"),
    DIVING("Menyelam");

    private final String displayName;

    FishBehavior(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
