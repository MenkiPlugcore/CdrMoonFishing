package id.menki.cdrmoonfishing.fishing;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.bait.BaitManager;
import id.menki.cdrmoonfishing.contracts.ContractManager;
import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.EncounterPhase;
import id.menki.cdrmoonfishing.model.FishBehavior;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import id.menki.cdrmoonfishing.rod.RodManager;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.CatchRecordResult;
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
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public final class FishingManager {
    private static final int BAR_LENGTH = 20;

    private final CdrMoonFishing plugin;
    private final FishRegistry registry;
    private final BaitManager baitManager;
    private final PlayerStatsManager statsManager;
    private final RodManager rodManager;
    private final ContractManager contractManager;
    private final Map<UUID, PreparedEncounter> prepared = new ConcurrentHashMap<>();
    private final Map<UUID, FishingSession> sessions = new ConcurrentHashMap<>();

    private final NamespacedKey fishIdKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey regionKey;
    private final NamespacedKey depthKey;
    private final NamespacedKey caughtAtKey;
    private final NamespacedKey behaviorKey;
    private final NamespacedKey baitKey;

    private BukkitTask ticker;

    public FishingManager(CdrMoonFishing plugin, FishRegistry registry, BaitManager baitManager,
                          PlayerStatsManager statsManager, RodManager rodManager, ContractManager contractManager) {
        this.plugin = plugin;
        this.registry = registry;
        this.baitManager = baitManager;
        this.statsManager = statsManager;
        this.rodManager = rodManager;
        this.contractManager = contractManager;
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.rarityKey = new NamespacedKey(plugin, "rarity");
        this.weightKey = new NamespacedKey(plugin, "weight_kg");
        this.regionKey = new NamespacedKey(plugin, "region");
        this.depthKey = new NamespacedKey(plugin, "depth");
        this.caughtAtKey = new NamespacedKey(plugin, "caught_at");
        this.behaviorKey = new NamespacedKey(plugin, "behavior");
        this.baitKey = new NamespacedKey(plugin, "bait_used");
        startTicker();
    }

    private void startTicker() {
        long period = Math.max(1L, plugin.getConfig().getLong("minigame.tick-period", 5L));
        this.ticker = plugin.getServer().getScheduler().runTaskTimer(plugin, this::tick, period, period);
    }

    public boolean prepareEncounter(Player player, Location hookLocation) {
        if (sessions.containsKey(player.getUniqueId())) return false;

        String region = hookLocation.getBlock().getBiome().toString().toLowerCase(Locale.ROOT);
        int depth = calculateDepth(hookLocation);
        String weather = weatherName(hookLocation.getWorld());
        String time = timeName(hookLocation.getWorld());
        BaitDefinition bait = baitManager.resolveSelected(player);

        FishDefinition fish = registry.select(region, depth, weather, time, bait,
                definition -> rodManager.rarityMultiplier(player, definition));
        if (fish == null) {
            prepared.remove(player.getUniqueId());
            player.sendActionBar(Component.text("Sepertinya tidak ada ikan yang tertarik di sini.", NamedTextColor.GRAY));
            return false;
        }

        String baitId = null;
        if (bait != null) {
            if (!baitManager.consume(player, bait)) {
                bait = null;
                fish = registry.select(region, depth, weather, time, null,
                        definition -> rodManager.rarityMultiplier(player, definition));
                if (fish == null) {
                    prepared.remove(player.getUniqueId());
                    return false;
                }
            } else {
                baitId = bait.id();
            }
        }

        prepared.put(player.getUniqueId(), new PreparedEncounter(fish, region, depth, weather, time, baitId));
        Component hint = Component.text(biteHint(fish.rarity()), rarityColor(fish.rarity()));
        if (bait != null) hint = hint.append(Component.text("  • " + bait.displayName(), NamedTextColor.GOLD));
        if (!fish.phases().isEmpty()) hint = hint.append(Component.text("  • Multi-Fase", NamedTextColor.LIGHT_PURPLE));
        player.sendActionBar(hint);
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.8f, 1.15f);
        return true;
    }

    public boolean startPrepared(Player player, Location fallbackLocation) {
        if (sessions.containsKey(player.getUniqueId())) return false;

        PreparedEncounter encounter = prepared.remove(player.getUniqueId());
        if (encounter == null) {
            prepareEncounter(player, fallbackLocation);
            encounter = prepared.remove(player.getUniqueId());
        }
        if (encounter == null) return false;

        double startTension = clamp(plugin.getConfig().getDouble("minigame.start-tension", 50.0), 0.0, 100.0);
        FishingSession session = new FishingSession(
                player.getUniqueId(), encounter.fish(), encounter.region(), encounter.depth(),
                encounter.weather(), encounter.time(), encounter.baitId(), startTension
        );

        sessions.put(player.getUniqueId(), session);
        EncounterPhase phase = syncPhase(player, session, true);
        if (phase == null) {
            player.sendTitle("§b§lIKAN TERSANGKUT!", "§7Perilaku: " + encounter.fish().behavior().displayName(), 5, 30, 10);
        }
        player.playSound(player.getLocation(), Sound.ENTITY_FISHING_BOBBER_RETRIEVE, 1.0f, 0.9f);
        sendBar(player, session);
        return true;
    }

    public void reelPulse(Player player) {
        FishingSession session = sessions.get(player.getUniqueId());
        if (session == null) return;

        long now = System.currentTimeMillis();
        long cooldown = Math.max(0L, plugin.getConfig().getLong("minigame.reel-cooldown-ms", 180L));
        if (now - session.lastPulseAt() < cooldown) return;

        session.lastPulseAt(now);
        EncounterPhase phase = session.fish().phaseAt(session.progress());
        double phaseMultiplier = phase == null ? 1.0 : phase.reelPowerMultiplier();
        double power = plugin.getConfig().getDouble("minigame.reel-power", 7.5)
                * phaseMultiplier
                * rodManager.reelMultiplier(player);
        session.tension(clamp(session.tension() + power, 0.0, 100.0));
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.35f, 1.55f);
        sendBar(player, session);
    }

    private void tick() {
        double dangerLow = plugin.getConfig().getDouble("minigame.danger-low", 5.0);
        double dangerHigh = plugin.getConfig().getDouble("minigame.danger-high", 95.0);
        int dangerGrace = Math.max(1, plugin.getConfig().getInt("minigame.danger-grace-ticks", 8));
        double slack = plugin.getConfig().getDouble("minigame.slack-per-tick", 2.0);
        double baseSafeProgress = plugin.getConfig().getDouble("minigame.progress-per-safe-tick", 4.0);
        double progressLoss = plugin.getConfig().getDouble("minigame.progress-loss-outside", 1.5);
        long timeoutMs = Math.max(5L, plugin.getConfig().getLong("minigame.timeout-seconds", 25L)) * 1000L;

        for (FishingSession session : new ArrayList<>(sessions.values())) {
            Player player = plugin.getServer().getPlayer(session.playerId());
            if (player == null || !player.isOnline()) {
                sessions.remove(session.playerId());
                continue;
            }

            FishDefinition fish = session.fish();
            EncounterPhase phase = syncPhase(player, session, false);
            FishBehavior behavior = phase == null ? fish.behavior() : phase.behavior();
            double pullMultiplier = phase == null ? 1.0 : phase.pullMultiplier();
            double progressMultiplier = phase == null ? 1.0 : phase.progressMultiplier();
            double[] safeRange = safeRange(phase);
            double safeMin = safeRange[0];
            double safeMax = safeRange[1];

            session.behaviorTicks(session.behaviorTicks() + 1);
            double pull = randomBetween(fish.pullMin(), fish.pullMax()) * pullMultiplier;
            double jitter = ThreadLocalRandom.current().nextDouble(-0.35, 0.36);
            double behaviorForce = behaviorForce(player, session, behavior);

            if (behavior == FishBehavior.CALM) {
                pull *= 0.85;
                jitter *= 0.5;
            } else if (behavior == FishBehavior.AGGRESSIVE) {
                pull *= 1.25;
            } else if (behavior == FishBehavior.DIVING) {
                pull *= 1.10;
            }

            session.tension(clamp(session.tension() + pull + behaviorForce - slack + jitter, 0.0, 100.0));

            boolean safe = session.tension() >= safeMin && session.tension() <= safeMax;
            if (safe) session.progress(clamp(session.progress() + (baseSafeProgress * progressMultiplier), 0.0, 100.0));
            else session.progress(clamp(session.progress() - progressLoss, 0.0, 100.0));

            boolean dangerous = session.tension() <= dangerLow || session.tension() >= dangerHigh;
            session.dangerTicks(dangerous ? session.dangerTicks() + 1 : Math.max(0, session.dangerTicks() - 1));

            syncPhase(player, session, false);
            sendBar(player, session);

            if (session.progress() >= 100.0) {
                completeCatch(player, session);
                continue;
            }
            if (session.dangerTicks() >= dangerGrace) {
                failCatch(player, session, session.tension() >= dangerHigh ? "Senarnya putus!" : "Senarnya terlalu kendur!");
                continue;
            }
            if (System.currentTimeMillis() - session.startedAt() >= timeoutMs) failCatch(player, session, "Ikannya lepas!");
        }
    }

    private EncounterPhase syncPhase(Player player, FishingSession session, boolean initial) {
        EncounterPhase phase = session.fish().phaseAt(session.progress());
        String nextId = phase == null ? null : phase.id();
        if (Objects.equals(session.activePhaseId(), nextId)) return phase;

        session.activePhaseId(nextId);
        session.behaviorTicks(0);
        session.dangerTicks(0);

        if (phase != null) {
            String title = phase.title() == null || phase.title().isBlank()
                    ? "§b§l" + phase.displayName().toUpperCase(Locale.ROOT)
                    : phase.title();
            String subtitle = phase.subtitle() == null || phase.subtitle().isBlank()
                    ? "§7Perilaku: " + phase.behavior().displayName()
                    : phase.subtitle();
            player.sendTitle(title, subtitle, initial ? 5 : 3, initial ? 32 : 25, 8);
            playPhaseSound(player, phase);
            if (!initial) {
                player.sendMessage(Component.text("⚡ Fase pertarungan: ", NamedTextColor.LIGHT_PURPLE)
                        .append(Component.text(phase.displayName(), NamedTextColor.AQUA)));
            }
        }
        return phase;
    }

    private void playPhaseSound(Player player, EncounterPhase phase) {
        if (phase.sound() == null || phase.sound().isBlank()) return;
        try {
            Sound sound = Sound.valueOf(phase.sound().toUpperCase(Locale.ROOT));
            player.playSound(player.getLocation(), sound, 0.9f, 0.95f);
        } catch (IllegalArgumentException ignored) {
            plugin.getLogger().warning("Invalid phase sound '" + phase.sound() + "' for fish phase '" + phase.id() + "'.");
        }
    }

    private double behaviorForce(Player player, FishingSession session, FishBehavior behavior) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double force = 0.0;

        switch (behavior) {
            case CALM -> { return 0.0; }
            case ERRATIC -> {
                double chance = clamp(plugin.getConfig().getDouble("behavior.erratic-surge-chance", 0.18), 0.0, 1.0);
                if (random.nextDouble() < chance) {
                    double strength = randomBetween(
                            plugin.getConfig().getDouble("behavior.erratic-surge-min", 2.5),
                            plugin.getConfig().getDouble("behavior.erratic-surge-max", 5.5));
                    force = random.nextBoolean() ? strength : -strength * 0.75;
                }
            }
            case AGGRESSIVE -> {
                double chance = clamp(plugin.getConfig().getDouble("behavior.aggressive-surge-chance", 0.14), 0.0, 1.0);
                if (random.nextDouble() < chance) {
                    force = randomBetween(
                            plugin.getConfig().getDouble("behavior.aggressive-surge-min", 3.5),
                            plugin.getConfig().getDouble("behavior.aggressive-surge-max", 7.0));
                }
            }
            case DIVING -> {
                int every = Math.max(2, plugin.getConfig().getInt("behavior.diving-surge-every-ticks", 8));
                if (session.behaviorTicks() % every == 0) {
                    force = randomBetween(
                            plugin.getConfig().getDouble("behavior.diving-surge-min", 5.0),
                            plugin.getConfig().getDouble("behavior.diving-surge-max", 8.0));
                }
            }
        }

        if (Math.abs(force) >= 2.0) {
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.45f, force > 0 ? 0.75f : 1.35f);
        }
        return force;
    }

    private void completeCatch(Player player, FishingSession session) {
        sessions.remove(player.getUniqueId());

        double weight = randomBetween(session.fish().minWeight(), session.fish().maxWeight());
        weight = Math.round(weight * 100.0) / 100.0;
        ItemStack reward = createFishItem(player, session, weight);

        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(reward);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));

        CatchRecordResult record = statsManager.recordCatch(player, session.fish(), weight);
        contractManager.recordCatch(player, session.fish(), weight, session);

        player.sendTitle("§a§lDAPAT!", "§f" + session.fish().displayName() + " §7• §b" + String.format(Locale.US, "%.2f kg", weight), 5, 45, 10);
        player.sendMessage(Component.text("Berhasil menangkap ", NamedTextColor.GRAY)
                .append(Component.text(session.fish().displayName(), rarityColor(session.fish().rarity())))
                .append(Component.text(" • " + String.format(Locale.US, "%.2f kg", weight) + " • kedalaman " + session.depth(), NamedTextColor.GRAY)));

        if (record.newDiscovery()) {
            player.sendMessage(Component.text("✦ PENEMUAN FISHDEX! ", NamedTextColor.AQUA)
                    .append(Component.text(session.fish().displayName(), rarityColor(session.fish().rarity())))
                    .append(Component.text(" • " + record.discoveredSpecies() + "/" + registry.definitions().size(), NamedTextColor.GRAY)));
        }
        if (record.newSpeciesRecord() && !record.newDiscovery()) {
            player.sendMessage(Component.text("★ Rekor spesies baru: " + String.format(Locale.US, "%.2f kg", weight), NamedTextColor.GREEN));
        }
        if (record.newOverallRecord()) player.sendMessage(Component.text("★ REKOR TANGKAPAN TERBESAR BARU!", NamedTextColor.GOLD));

        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, record.newDiscovery() ? 1.45f : 1.2f);
    }

    private ItemStack createFishItem(Player player, FishingSession session, double weight) {
        FishDefinition fish = session.fish();
        ItemStack item = new ItemStack(fish.material());
        ItemMeta meta = item.getItemMeta();

        meta.displayName(Component.text(fish.displayName(), rarityColor(fish.rarity())).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(fish.rarity().displayName(), rarityColor(fish.rarity())));
        if (fish.phases().isEmpty()) lore.add(Component.text("Perilaku: " + fish.behavior().displayName(), NamedTextColor.GRAY));
        else lore.add(Component.text("Pertarungan: " + fish.phases().size() + " fase", NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.text("Berat: " + String.format(Locale.US, "%.2f kg", weight), NamedTextColor.GRAY));
        if (session.baitId() != null) {
            BaitDefinition bait = baitManager.registry().get(session.baitId());
            lore.add(Component.text("Umpan: " + (bait == null ? session.baitId() : bait.displayName()), NamedTextColor.DARK_GRAY));
        }
        lore.add(Component.text("Wilayah: " + session.region(), NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Kedalaman: " + session.depth() + " blok", NamedTextColor.DARK_GRAY));
        lore.add(Component.text("Ditangkap oleh: " + player.getName(), NamedTextColor.DARK_GRAY));
        meta.lore(lore);

        meta.getPersistentDataContainer().set(fishIdKey, PersistentDataType.STRING, fish.id());
        meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, fish.rarity().name());
        meta.getPersistentDataContainer().set(weightKey, PersistentDataType.DOUBLE, weight);
        meta.getPersistentDataContainer().set(regionKey, PersistentDataType.STRING, session.region());
        meta.getPersistentDataContainer().set(depthKey, PersistentDataType.INTEGER, session.depth());
        meta.getPersistentDataContainer().set(caughtAtKey, PersistentDataType.LONG, System.currentTimeMillis());
        meta.getPersistentDataContainer().set(behaviorKey, PersistentDataType.STRING, fish.behavior().name());
        if (session.baitId() != null) meta.getPersistentDataContainer().set(baitKey, PersistentDataType.STRING, session.baitId());

        item.setItemMeta(meta);
        return item;
    }

    private void failCatch(Player player, FishingSession session, String reason) {
        sessions.remove(player.getUniqueId());
        player.sendTitle("§c§lLEPAS", "§7" + reason, 5, 35, 10);
        player.playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 0.8f, 0.8f);
    }

    public void cancel(Player player, boolean notify) {
        prepared.remove(player.getUniqueId());
        FishingSession removed = sessions.remove(player.getUniqueId());
        if (notify && removed != null) player.sendActionBar(Component.text("Pertarungan memancing dibatalkan.", NamedTextColor.GRAY));
    }

    public void clearPrepared(Player player) { prepared.remove(player.getUniqueId()); }
    public boolean isActive(Player player) { return sessions.containsKey(player.getUniqueId()); }
    public boolean hasPrepared(Player player) { return prepared.containsKey(player.getUniqueId()); }
    public FishingSession session(Player player) { return sessions.get(player.getUniqueId()); }
    public int activeCount() { return sessions.size(); }
    public int preparedCount() { return prepared.size(); }

    public void shutdown() {
        if (ticker != null) ticker.cancel();
        prepared.clear();
        sessions.clear();
    }

    private int calculateDepth(Location hookLocation) {
        World world = hookLocation.getWorld();
        if (world == null) return 0;

        int x = hookLocation.getBlockX();
        int z = hookLocation.getBlockZ();
        int y = hookLocation.getBlockY();

        if (!isWaterColumn(world.getBlockAt(x, y, z).getType())) {
            int belowY = y - 1;
            if (belowY < world.getMinHeight() || !isWaterColumn(world.getBlockAt(x, belowY, z).getType())) return 0;
            y = belowY;
        }

        int depth = 0;
        while (y >= world.getMinHeight() && depth < 64) {
            Material material = world.getBlockAt(x, y, z).getType();
            if (!isWaterColumn(material)) break;
            depth++;
            y--;
        }
        return depth;
    }

    private boolean isWaterColumn(Material material) {
        return material == Material.WATER || material == Material.BUBBLE_COLUMN;
    }

    private String weatherName(World world) {
        if (world == null) return "CLEAR";
        if (world.isThundering()) return "THUNDER";
        return world.hasStorm() ? "RAIN" : "CLEAR";
    }

    private String timeName(World world) {
        if (world == null) return "DAY";
        long time = world.getTime();
        return time >= 13000L && time <= 23000L ? "NIGHT" : "DAY";
    }

    private void sendBar(Player player, FishingSession session) {
        EncounterPhase phase = session.fish().phaseAt(session.progress());
        FishBehavior behavior = phase == null ? session.fish().behavior() : phase.behavior();
        double[] safe = safeRange(phase);
        int filled = (int) Math.round((session.tension() / 100.0) * BAR_LENGTH);
        filled = Math.max(0, Math.min(BAR_LENGTH, filled));

        String bar = "▰".repeat(filled) + "▱".repeat(BAR_LENGTH - filled);
        NamedTextColor barColor = session.tension() >= safe[0] && session.tension() <= safe[1]
                ? NamedTextColor.GREEN
                : (session.tension() <= 10.0 || session.tension() >= 90.0 ? NamedTextColor.RED : NamedTextColor.YELLOW);
        String state = phase == null ? behavior.displayName().toUpperCase(Locale.ROOT) : phase.displayName().toUpperCase(Locale.ROOT);

        Component actionBar = Component.text("[" + state + "] ", phase == null ? NamedTextColor.DARK_AQUA : NamedTextColor.LIGHT_PURPLE)
                .append(Component.text("Senar ", NamedTextColor.AQUA))
                .append(Component.text(bar, barColor))
                .append(Component.text(String.format(Locale.US, " %.0f%%  Tangkap %.0f%%", session.tension(), session.progress()), NamedTextColor.GRAY));
        player.sendActionBar(actionBar);
    }

    private double[] safeRange(EncounterPhase phase) {
        double min = plugin.getConfig().getDouble("minigame.perfect-min", 35.0);
        double max = plugin.getConfig().getDouble("minigame.perfect-max", 70.0);
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

    private String biteHint(FishRarity rarity) {
        return switch (rarity) {
            case COMMON, UNCOMMON -> "Ada ikan yang menyambar...";
            case RARE, EPIC -> "Ada sesuatu yang tidak biasa menyambar...";
            case LEGENDARY -> "SESUATU YANG BESAR memakan umpannya...";
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
        double low = Math.min(min, max);
        double high = Math.max(min, max);
        if (high <= low) return low;
        return ThreadLocalRandom.current().nextDouble(low, high);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
