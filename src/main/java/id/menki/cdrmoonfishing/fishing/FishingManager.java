package id.menki.cdrmoonfishing.fishing;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingManager {
    private static final int BAR_LENGTH = 20;

    private final CdrMoonFishing plugin;
    private final FishRegistry registry;
    private final Map<UUID, PreparedEncounter> prepared = new ConcurrentHashMap<>();
    private final Map<UUID, FishingSession> sessions = new ConcurrentHashMap<>();

    private final NamespacedKey fishIdKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey regionKey;
    private final NamespacedKey depthKey;
    private final NamespacedKey caughtAtKey;

    private BukkitTask ticker;

    public FishingManager(CdrMoonFishing plugin, FishRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.rarityKey = new NamespacedKey(plugin, "rarity");
        this.weightKey = new NamespacedKey(plugin, "weight_kg");
        this.regionKey = new NamespacedKey(plugin, "region");
        this.depthKey = new NamespacedKey(plugin, "depth");
        this.caughtAtKey = new NamespacedKey(plugin, "caught_at");
        startTicker();
    }

    private void startTicker() {
        long period = Math.max(1L, plugin.getConfig().getLong("minigame.tick-period", 5L));
        this.ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public boolean prepareEncounter(Player player, Location hookLocation) {
        if (sessions.containsKey(player.getUniqueId())) {
            return false;
        }

        String region = hookLocation.getBlock().getBiome().toString().toLowerCase(Locale.ROOT);
        int depth = calculateDepth(hookLocation);
        String weather = weatherName(hookLocation.getWorld());
        String time = timeName(hookLocation.getWorld());

        FishDefinition fish = registry.select(region, depth, weather, time);
        if (fish == null) {
            prepared.remove(player.getUniqueId());
            player.sendActionBar(Component.text("No fish seems interested in this spot.", NamedTextColor.GRAY));
            return false;
        }

        prepared.put(player.getUniqueId(), new PreparedEncounter(fish, region, depth, weather, time));
        player.sendActionBar(Component.text(biteHint(fish.rarity()), rarityColor(fish.rarity())));
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.15f);
        return true;
    }

    public boolean startPrepared(Player player, Location fallbackLocation) {
        if (sessions.containsKey(player.getUniqueId())) {
            return false;
        }

        PreparedEncounter encounter = prepared.remove(player.getUniqueId());
        if (encounter == null) {
            prepareEncounter(player, fallbackLocation);
            encounter = prepared.remove(player.getUniqueId());
        }

        if (encounter == null) {
            return false;
        }

        double startTension = clamp(plugin.getConfig().getDouble("minigame.start-tension", 50.0), 0.0, 100.0);
        FishingSession session = new FishingSession(
                player.getUniqueId(),
                encounter.fish(),
                encounter.region(),
                encounter.depth(),
                startTension
        );

        sessions.put(player.getUniqueId(), session);
        player.sendTitle("§b§lFISH ON!", "§7Keep the tension in the safe zone", 5, 30, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.9f);
        sendBar(player, session);
        return true;
    }

    public void reelPulse(Player player) {
        FishingSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, plugin.getConfig().getLong("minigame.reel-cooldown-ms", 180L));
        if (now - session.lastPulseAt() < cooldown) {
            return;
        }

        session.lastPulseAt(now);
        double power = plugin.getConfig().getDouble("minigame.reel-power", 7.5);
        session.tension(clamp(session.tension() + power, 0.0, 100.0));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.55f);
        sendBar(player, session);
    }

    private void tick() {
        double safeMin = plugin.getConfig().getDouble("minigame.perfect-min", 35.0);
        double safeMax = plugin.getConfig().getDouble("minigame.perfect-max", 70.0);
        double dangerLow = plugin.getConfig().getDouble("minigame.danger-low", 5.0);
        double dangerHigh = plugin.getConfig().getDouble("minigame.danger-high", 95.0);
        int dangerGrace = Math.max(1, plugin.getConfig().getInt("minigame.danger-grace-ticks", 8));
        double slack = plugin.getConfig().getDouble("minigame.slack-per-tick", 2.0);
        double safeProgress = plugin.getConfig().getDouble("minigame.progress-per-safe-tick", 4.0);
        double progressLoss = plugin.getConfig().getDouble("minigame.progress-loss-outside", 1.5);
        long timeoutMs = Math.max(5L, plugin.getConfig().getLong("minigame.timeout-seconds", 25L)) * 1000L;

        for (FishingSession session : new ArrayList<>(sessions.values())) {
            Player player = plugin.getServer().getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                sessions.remove(session.playerId());
                continue;
            }

            FishDefinition fish = session.fish();
            double pull = randomBetween(fish.pullMin(), fish.pullMax());
            double jitter = ThreadLocalRandom.current().nextDouble(-0.35, 0.36);
            session.tension(clamp(session.tension() + pull - slack + jitter, 0.0, 100.0));

            boolean safe = session.tension() >= safeMin && session.tension() <= safeMax;
            if (safe) {
                session.progress(clamp(session.progress() + safeProgress, 0.0, 100.0));
            } else {
                session.progress(clamp(session.progress() - progressLoss, 0.0, 100.0));
            }

            boolean dangerous = session.tension() <= dangerLow || session.tension() >= dangerHigh;
            session.dangerTicks(dangerous ? session.dangerTicks() + 1 : Math.max(0, session.dangerTicks() - 1));

            sendBar(player, session);

            if (session.progress() >= 100.0) {
                completeCatch(player, session);
                continue;
            }

            if (session.dangerTicks() >= dangerGrace) {
                failCatch(player, session, session.tension() >= dangerHigh ? "The line snapped!" : "The line went slack!");
                continue;
            }

            if (System.currentTimeMillis() - session.startedAt() >= timeoutMs) {
                failCatch(player, session, "The fish escaped!");
            }
        }
    }

    private void completeCatch(Player player, FishingSession session) {
        sessions.remove(player.getUniqueId());

        double weight = randomBetween(session.fish().minWeight(), session.fish().maxWeight());
        weight = Math.round(weight * 100.0) / 100.0;
        ItemStack reward = createFishItem(player, session, weight);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        player.sendTitle("§a§lCATCH!", "§f" + session.fish().displayName() + " §7• §b" + String.format(Locale.US, "%.2f kg", weight), 5, 45, 10);
        player.sendMessage(Component.text("Caught ", NamedTextColor.GRAY)
                .append(Component.text(session.fish().displayName(), rarityColor(session.fish().rarity())))
                .append(Component.text(" • " + String.format(Locale.US, "%.2f kg", weight) + " • depth " + session.depth(), NamedTextColor.GRAY)));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
    }

    private ItemStack createFishItem(Player player, FishingSession session, double weight) {
        FishDefinition fish = session.fish();
        ItemStack item = new ItemStack(fish.material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(fish.displayName(), rarityColor(fish.rarity())).decorate(TextDecoration.BOLD));
        List<Component> lore = List.of(
                Component.text(fish.rarity().displayName(), rarityColor(fish.rarity())),
                Component.text("Weight: " + String.format(Locale.US, "%.2f kg", weight), NamedTextColor.GRAY),
                Component.text("Region: " + session.region(), NamedTextColor.DARK_GRAY),
                Component.text("Depth: " + session.depth() + " blocks", NamedTextColor.DARK_GRAY),
                Component.text("Caught by: " + player.getName(), NamedTextColor.DARK_GRAY)
        );
        meta.lore(lore);

        meta.getPersistentDataContainer().set(fishIdKey, PersistentDataType.STRING, fish.id());
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, fish.rarity().name());
        meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);
        meta.getPersistentDataContainer().set(regionKey, PersistentDataType.STRING, session.region());
        meta.getPersistentDataContainer().set(depthKey, PersistentDataType.INTEGER, session.depth());
        meta.getPersistentDataContainer().set(caughtAtKey, PersistentDataType.LONG, System.currentTimeMillis());

        item.setItemMeta(meta);
        return item;
    }

    private void failCatch(Player player, FishingSession session, String reason) {
        sessions.remove(player.getUniqueId());
        player.sendTitle("§c§lESCAPED", "§7" + reason, 5, 35, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
    }

    public void cancel(Player player, boolean notify) {
        prepared.remove(player.getUniqueId());
        FishingSession removed = sessions.remove(player.getUniqueId());
        if (notify && removed != null) {
            player.sendActionBar(Component.text("Fishing encounter cancelled.", NamedTextColor.GRAY));
        }
    }

    public void clearPrepared(Player player) {
        prepared.remove(player.getUniqueId());
    }

    public boolean isActive(Player player) {
        return sessions.containsKey(player.getUniqueId());
    }

    public boolean hasPrepared(Player player) {
        return prepared.containsKey(player.getUniqueId());
    }

    public FishingSession session(Player player) {
        return sessions.get(player.getUniqueId());
    }

    public int activeCount() {
        return sessions.size();
    }

    public int preparedCount() {
        return prepared.size();
    }

    public void shutdown() {
        if (ticker != null) {
            ticker.cancel();
        }
        prepared.clear();
        sessions.clear();
    }

    private int calculateDepth(Location hookLocation) {
        World world = hookLocation.getWorld();
        if (world == null) {
            return 0;
        }

        int depth = 0;
        int x = hookLocation.getBlockX();
        int z = hookLocation.getBlockZ();
        int y = hookLocation.getBlockY() - 1;
        int floor = Math.max(world.getMinHeight(), y - 64);

        while (y >= floor) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (material != Material.WATER && material != Material.BUBBLE_COLUMN) {
                break;
            }
            depth++;
            y--;
        }

        return depth;
    }

    private String weatherName(World world) {
        if (world == null) {
            return "CLEAR";
        }
        if (world.isThundering()) {
            return "THUNDER";
        }
        return world.hasStorm() ? "RAIN" : "CLEAR";
    }

    private String timeName(World world) {
        if (world == null) {
            return "DAY";
        }
        long time = world.getTime();
        return time >= 13000L && time <= 23000L ? "NIGHT" : "DAY";
    }

    private void sendBar(Player player, FishingSession session) {
        double safeMin = plugin.getConfig().getDouble("minigame.perfect-min", 35.0);
        double safeMax = plugin.getConfig().getDouble("minigame.perfect-max", 70.0);
        int filled = (int) Math.round((session.tension() / 100.0) * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));

        String bar = "▰".repeat(filled) + "▱".repeat(BAR_LENGTH - filled);
        NamedTextColor barColor = session.tension() >= safeMin && session.tension() <= safeMax
                ? NamedTextColor.GREEN
                : (session.tension() <= 10.0 || session.tension() >= 90.0 ? NamedTextColor.RED : NamedTextColor.YELLOW);

        Component actionBar = Component.text("Tension ", NamedTextColor.AQUA)
                .append(Component.text(bar, barColor))
                .append(Component.text(String.format(Locale.US, " %.0f%%  Catch %.0f%%", session.tension(), session.progress()), NamedTextColor.GRAY));
        player.sendActionBar(actionBar);
    }

    private String biteHint(FishRarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON -> "Something is biting...";
            case RARE, EPIC -> "Something unusual is biting...";
            case LEGENDARY -> "Something HUGE took the bait...";
        };
    }

    private NamedTextColor rarityColor(FishRarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
    }

    private double randomBetween(double min, double max) {
        if (max <= min) {
            return min;
        }
        return ThreadLocalRandom.current().nextDouble(min, max);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
