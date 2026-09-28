package id.menki.cdrmoonfishing.leaderboard;

import java.util.Locale;

public enum LeaderboardMetric {
    CATCHES("Total Tangkapan"),
    WEIGHT("Total Berat"),
    BIGGEST("Tangkapan Terbesar"),
    LEGENDARY("Tangkapan Legendaris");

    private final String displayName;

    LeaderboardMetric(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static LeaderboardMetric parse(String raw) {
        if (raw == null || raw.isBlank()) return CATCHES;
        String value = raw.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        if (value.equals("TOTAL") || value.equals("TOTAL_CATCHES")) return CATCHES;
        if (value.equals("TOTAL_WEIGHT")) return WEIGHT;
        if (value.equals("BIG") || value.equals("HEAVIEST")) return BIGGEST;
        if (value.equals("LEGENDARIES")) return LEGENDARY;
        try {
            return valueOf(value);
        } catch (IllegalArgumentException ignored) {
            return CATCHES;
        }
    }
}
