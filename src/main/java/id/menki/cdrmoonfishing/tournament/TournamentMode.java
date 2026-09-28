package id.menki.cdrmoonfishing.tournament;

import java.util.Locale;

public enum TournamentMode {
    POINTS("Poin"),
    TOTAL_WEIGHT("Total Berat"),
    BIGGEST("Tangkapan Terbesar");

    private final String displayName;

    TournamentMode(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static TournamentMode parse(String raw) {
        if (raw == null || raw.isBlank()) return POINTS;
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (value.equals("WEIGHT") || value.equals("TOTAL")) return TOTAL_WEIGHT;
        if (value.equals("BIG") || value.equals("HEAVIEST")) return BIGGEST;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return POINTS;
        }
    }
}
