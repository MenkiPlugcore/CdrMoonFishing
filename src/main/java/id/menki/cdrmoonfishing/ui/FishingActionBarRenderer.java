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
 * Lane-style action bar renderer for active fishing encounters.
 * FishingManager owns gameplay state; this class only visualizes it.
 */
public final class FishingActionBarRenderer {
    private static final TextColor SAFE = TextColor.color(0xF5F7FA);
    private static final TextColor WARNING = TextColor.color(0xB8C2CC);
    private static final TextColor DANGER = TextColor.color(0x66717D);
    private static final TextColor FRAME = TextColor.color(0xA9B4C0);
    private static final TextColor MARKER = TextColor.color(0x61D7FF);
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

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }
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
                plugin.getConfig().getInt("ui.action-bar.segments", 20)));
        String safeGlyph = glyph("ui.action-bar.safe-segment", "█");
        String warningGlyph = glyph("ui.action-bar.warning-segment", "▒");
        String dangerGlyph = glyph("ui.action-bar.danger-segment", "░");
        String markerGlyph = glyph("ui.action-bar.marker", "│");
        String leftFrame = plugin.getConfig().getString("ui.action-bar.left-frame", "[");
        String rightFrame = plugin.getConfig().getString("ui.action-bar.right-frame", "]");
        String prefix = plugin.getConfig().getString("ui.action-bar.prefix", "🎣 ");

        double tension = clamp(session.tension(), 0.0, 100.0);
        double[] safe = safeRange(session);
        double dangerLow = clamp(plugin.getConfig().getDouble("minigame.danger-low", 5.0), 0.0, 100.0);
        double dangerHigh = clamp(plugin.getConfig().getDouble("minigame.danger-high", 95.0), 0.0, 100.0);

        int markerIndex = (int) Math.round((tension / 100.0) * (segments - 1));
        markerIndex = Math.max(0, Math.min(segments - 1, markerIndex));

        Component lane = Component.text(prefix == null ? "" : prefix, NamedTextColor.AQUA)
                .append(Component.text(leftFrame == null ? "[" : leftFrame, FRAME));

        for (int index = 0; index < segments; index++) {
            double position = segments <= 1 ? 0.0 : (index * 100.0 / (segments - 1));
            if (index == markerIndex) {
                lane = lane.append(Component.text(markerGlyph, MARKER).decorate(TextDecoration.BOLD));
                continue;
            }

            if (position <= dangerLow || position >= dangerHigh) {
                lane = lane.append(Component.text(dangerGlyph, DANGER));
            } else if (position >= safe[0] && position <= safe[1]) {
                lane = lane.append(Component.text(safeGlyph, SAFE));
            } else {
                lane = lane.append(Component.text(warningGlyph, WARNING));
            }
        }

        lane = lane.append(Component.text(rightFrame == null ? "]" : rightFrame, FRAME));

        if (plugin.getConfig().getBoolean("ui.action-bar.show-catch-progress", true)) {
            lane = lane.append(Component.text(String.format(Locale.US, " %.0f%%", session.progress()), PROGRESS));
        }

        if (plugin.getConfig().getBoolean("ui.action-bar.show-tension-percent", false)) {
            lane = lane.append(Component.text(String.format(Locale.US, "  T %.0f%%", tension), NamedTextColor.GRAY));
        }

        if (plugin.getConfig().getBoolean("ui.action-bar.show-state", false)) {
            EncounterPhase phase = session.fish().phaseAt(session.progress());
            String state = phase == null ? session.fish().behavior().displayName() : phase.displayName();
            lane = lane.append(Component.text("  " + state, NamedTextColor.LIGHT_PURPLE));
        }

        return lane;
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
