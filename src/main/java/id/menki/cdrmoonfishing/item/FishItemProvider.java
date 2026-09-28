package id.menki.cdrmoonfishing.item;

import java.util.Locale;

public enum FishItemProvider {
    VANILLA,
    ITEMSADDER,
    MMOITEMS,
    AUTO;

    public static FishItemProvider parse(String raw) {
        if (raw == null || raw.isBlank()) return VANILLA;
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return VANILLA;
        }
    }
}
