package id.menki.cdrmoonfishing.ui;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.fishing.FishingSession;
import id.menki.cdrmoonfishing.model.EncounterPhase;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Locale;

/**
 * High-frequency visual action-bar overlay for active fishing encounters.
 *
 * FishingManager still owns all gameplay state. This renderer only presents that
 * state as a readable moving marker over danger/warning/safe zones.
 */
public final class FishingActionBarRenderer {
    private static final TextColor SAFE = TextColor.color(0x55E66A);
    private static final TextColor WARNING = TextColor.color(0xFFD34E);
    private static final TextColor DANGER = TextColor.color(0xFF5555);
    private static final TextColor FRAME = TextColor.color(0x6D7B8A);
    private static final TextColor MARKER = TextColor.color(0xF8FBFF);
    private static final TextColor PROGRESS = TextColor.color(0x61D7FF);

    private final CdrMoonFishing plugin;
    private BukkitTask task;

    public FishingActionBarRenderer(CdrMoonFishing plugin) {
        this.plugin = plugin;
    }

    public void start() {
        if (task != null) task.cancel();
        task = plugin.getServer().getScheduler().runTaskTimer(plugin, this::renderActiveSessions, 1L, 1L);
    }

    private void renderActiveSessions() {
        if (!plugin.getConfig().getBoolean("ui.action-bar.enabled", true)) return;
        if (plugin.getFishingManager() == null || plugin.getFishingManager().activeCount() <= 0) return;

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            FishingSession session = plugin.getFishingManager().session(player);
            if (session != null) player.sendActionBar(render(session));
        }
    }

    private Component render(FishingSession session) {
        int segments = Math.max(12, Math.min(40,
                plugin.getConfig().getInt("ui.action-bar.segments", 24)));
        String segmentGlyph = glyph("ui.action-bar.segment", "▬");
        String markerGlyph = glyph("ui.action-bar.marker", "◆");

        double tension = clamp(session.tension(), 0.0, 100.0);
        double[] safe = safeRange(session);
        double dangerLow = clamp(plugin.getConfig().getDouble("minigame.danger-low", 5.0), 0.0, 100.0);
        double dangerHigh = clamp(plugin.getConfig().getDouble("minigame.danger-high", 95.0), 0.0, 100.0);

        int markerIndex = (int) Math.round((tension / 100.0) * (segments - 1));
        markerIndex = Math.max(0, Math.min(segments - 1, markerIndex));

        Component bar = Component.text("[", FRAME);
        for (int index = 0; index < segments; index++) {
            double position = segments <= 1 ? 0.0 : (index * 100.0 / (segments - 1));
            if (index == markerIndex) {
                bar = bar.append(Component.text(markerGlyph, MARKER).decorate(TextDecoration.BOLD));
            } else {
                bar = bar.append(Component.text(segmentGlyph, zoneColor(position, safe[0], safe[1], dangerLow, dangerHigh)));
            }
        }
        bar = bar.append(Component.text("]", FRAME));

        Component result = Component.text("LINE ", NamedTextColor.AQUA)
                .append(bar);

        if (plugin.getConfig().getBoolean("ui.action-bar.show-tension-percent", true)) {
            result = result.append(Component.text(String.format(Locale.US, " %.0f%%", tension), NamedTextColor.WHITE));
        }

        if (plugin.getConfig().getBoolean("ui.action-bar.show-catch-progress", true)) {
            result = result.append(Component.text(String.format(Locale.US, "  CATCH %.0f%%", session.progress()), PROGRESS));
        }

        if (plugin.getConfig().getBoolean("ui.action-bar.show-state", false)) {
            EncounterPhase phase = session.fish().phaseAt(session.progress());
            String state = phase == null ? session.fish().behavior().displayName() : phase.displayName();
            result = result.append(Component.text("  " + state, NamedTextColor.LIGHT_PURPLE));
        }

        return result;
    }

    private TextColor zoneColor(double position, double safeMin, double safeMax,
                                double dangerLow, double dangerHigh) {
        if (position <= dangerLow || position >= dangerHigh) return DANGER;
        if (position >= safeMin && position <= safeMax) return SAFE;
        return WARNING;
    }

    private double[] safeRange(FishingSession session) {
        double min = plugin.getConfig().getDouble("minigame.perfect-min", 35.0);
        double max = plugin.getConfig().getDouble("minigame.perfect-max", 70.0);
        EncounterPhase phase = session.fish().phaseAt(session.progress());
        if (phase != null) {
            min += phase.safeMinOffset();
            max += phase.safeMaxOffset();
        }
        min = clamp(min, 0.0, 95.0);
        max = clamp(max, 5.0, 100.0);
        if (max - min < 5.0) {
            max = Math.min(100.0, min + 5.0);
            if (max - min < 5.0) min = Math.max(0.0, max - 5.0);
        }
        return new double[]{min, max};
    }

    private String glyph(String path, String fallback) {
        String value = plugin.getConfig().getString(path, fallback);
        if (value == null || value.isBlank()) return fallback;
        return value.substring(0, value.offsetByCodePoints(0, 1));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
