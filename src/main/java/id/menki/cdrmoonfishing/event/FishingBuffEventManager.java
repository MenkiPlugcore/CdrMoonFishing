package id.menki.cdrmoonfishing.event;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.model.FishDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Global timed fishing luck event. The displayed x2/x4/x8 value is a luck
 * intensity, not a literal weight multiplier. Rarity is re-weighted toward
 * higher tiers and catch weight is biased toward each species' configured
 * max-weight while never exceeding it.
 */
public final class FishingBuffEventManager {
    private static final Set<Integer> ALLOWED_MULTIPLIERS = Set.of(2, 4, 8);
    private static final long MAX_DURATION_MS = 24L * 60L * 60L * 1000L;

    private final CdrMoonFishing plugin;
    private final File stateFile;
    private final Set<Integer> announcedMinutes = new HashSet<>();

    private boolean active;
    private int multiplier = 1;
    private long startedAt;
    private long endsAt;
    private BukkitTask ticker;

    public FishingBuffEventManager(CdrMoonFishing plugin) {
        this.plugin = plugin;
        this.stateFile = new File(plugin.getDataFolder(), "fishing-event.yml");
        loadState();
        this.ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public synchronized StartResult start(int requestedMultiplier, long durationMillis) {
        if (!ALLOWED_MULTIPLIERS.contains(requestedMultiplier)) {
            return new StartResult(false, "Multiplier hanya boleh 2x, 4x, atau 8x.");
        }
        if (active && System.currentTimeMillis() < endsAt) {
            return new StartResult(false, "Fishing Fever masih aktif. Hentikan event lama terlebih dahulu.");
        }

        long safeDuration = Math.max(60_000L, Math.min(MAX_DURATION_MS, durationMillis));
        long now = System.currentTimeMillis();
        this.active = true;
        this.multiplier = requestedMultiplier;
        this.startedAt = now;
        this.endsAt = now + safeDuration;
        this.announcedMinutes.clear();
        saveState();

        broadcast(Component.text("🔥 FISHING FEVER x" + multiplier + " DIMULAI!", NamedTextColor.GOLD));
        broadcast(Component.text("Durasi: " + formatDuration(safeDuration) + " • ikan langka dan ikan besar lebih mudah didapat.", NamedTextColor.AQUA));
        broadcast(Component.text("Buff meningkatkan luck rarity dan membias berat ke max-weight spesies, bukan melewati batas beratnya.", NamedTextColor.GRAY));
        return new StartResult(true, "Event dimulai.");
    }

    public synchronized boolean stop(boolean announce) {
        if (!active) return false;
        active = false;
        multiplier = 1;
        startedAt = 0L;
        endsAt = 0L;
        announcedMinutes.clear();
        saveState();
        if (announce) broadcast(Component.text("Fishing Fever telah berakhir.", NamedTextColor.GRAY));
        return true;
    }

    /**
     * Multiplier applied to FishRegistry's weighted selection.
     * Common stays neutral while progressively rarer tiers receive more of
     * the event intensity. Values are configurable in config.yml.
     */
    public synchronized double rarityMultiplier(FishDefinition fish) {
        if (!isActiveInternal() || fish == null) return 1.0;
        double strength = switch (fish.rarity()) {
            case COMMON -> plugin.getConfig().getDouble("fishing-event.rarity-strength.COMMON", 0.0);
            case UNCOMMON -> plugin.getConfig().getDouble("fishing-event.rarity-strength.UNCOMMON", 0.15);
            case RARE -> plugin.getConfig().getDouble("fishing-event.rarity-strength.RARE", 0.45);
            case EPIC -> plugin.getConfig().getDouble("fishing-event.rarity-strength.EPIC", 0.75);
            case LEGENDARY -> plugin.getConfig().getDouble("fishing-event.rarity-strength.LEGENDARY", 1.0);
        };
        strength = Math.max(0.0, strength);
        return Math.max(0.0, 1.0 + ((multiplier - 1.0) * strength));
    }

    /**
     * Rolls within the original min/max range. x2/x4/x8 increasingly biases
     * the result toward maxWeight without changing the species hard limit.
     */
    public synchronized double rollWeight(double minWeight, double maxWeight) {
        double min = Math.min(minWeight, maxWeight);
        double max = Math.max(minWeight, maxWeight);
        if (max <= min) return min;

        double u = ThreadLocalRandom.current().nextDouble();
        if (!isActiveInternal()) return min + ((max - min) * u);

        double scale = Math.max(0.05, plugin.getConfig().getDouble("fishing-event.big-fish-power-scale", 1.0));
        double effectiveLuck = 1.0 + ((multiplier - 1.0) * scale);
        double ratio = Math.pow(u, 1.0 / Math.max(1.0, effectiveLuck));
        return min + ((max - min) * ratio);
    }

    private void tick() {
        synchronized (this) {
            if (!active) return;
            long now = System.currentTimeMillis();
            if (now >= endsAt) {
                stop(true);
                return;
            }

            long seconds = Math.max(0L, (endsAt - now + 999L) / 1000L);
            announceMinuteThreshold(seconds, 10);
            announceMinuteThreshold(seconds, 5);
            announceMinuteThreshold(seconds, 1);
        }
    }

    private void announceMinuteThreshold(long remainingSeconds, int minutes) {
        long threshold = minutes * 60L;
        if (remainingSeconds <= threshold && remainingSeconds > threshold - 2L && announcedMinutes.add(minutes)) {
            broadcast(Component.text("🔥 Fishing Fever x" + multiplier + " tersisa " + minutes + " menit.", NamedTextColor.YELLOW));
        }
    }

    private void loadState() {
        if (!stateFile.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        this.active = yaml.getBoolean("active", false);
        this.multiplier = yaml.getInt("multiplier", 1);
        this.startedAt = yaml.getLong("started-at", 0L);
        this.endsAt = yaml.getLong("ends-at", 0L);

        if (!ALLOWED_MULTIPLIERS.contains(multiplier)) multiplier = 1;
        if (active && (multiplier == 1 || endsAt <= System.currentTimeMillis())) {
            active = false;
            multiplier = 1;
            startedAt = 0L;
            endsAt = 0L;
            saveState();
        }
    }

    private void saveState() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("active", active);
        yaml.set("multiplier", multiplier);
        yaml.set("started-at", startedAt);
        yaml.set("ends-at", endsAt);
        try {
            yaml.save(stateFile);
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save fishing-event.yml: " + ex.getMessage());
        }
    }

    private boolean isActiveInternal() {
        if (!active) return false;
        if (endsAt <= System.currentTimeMillis()) {
            stop(true);
            return false;
        }
        return true;
    }

    private void broadcast(Component component) {
        plugin.getServer().getConsoleSender().sendMessage(component);
        for (Player player : plugin.getServer().getOnlinePlayers()) player.sendMessage(component);
    }

    public synchronized boolean isActive() { return isActiveInternal(); }
    public synchronized int multiplier() { return isActiveInternal() ? multiplier : 1; }
    public synchronized long startedAt() { return startedAt; }
    public synchronized long endsAt() { return endsAt; }
    public synchronized long remainingMillis() {
        return isActiveInternal() ? Math.max(0L, endsAt - System.currentTimeMillis()) : 0L;
    }

    public String formatDuration(long millis) {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        if (hours > 0L) return String.format(Locale.US, "%dh %02dm %02ds", hours, minutes, seconds);
        return String.format(Locale.US, "%dm %02ds", minutes, seconds);
    }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        synchronized (this) { saveState(); }
    }

    public record StartResult(boolean success, String message) {}
}
