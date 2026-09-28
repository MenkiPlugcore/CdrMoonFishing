package id.menki.cdrmoonfishing.milestone;

import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.registry.BaitRegistry;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import id.menki.cdrmoonfishing.rod.RodManager;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class FishDexMilestoneManager {
    private final JavaPlugin plugin;
    private final PlayerStatsManager statsManager;
    private final FishRegistry fishRegistry;
    private final BaitRegistry baitRegistry;
    private final RodManager rodManager;
    private final VaultEconomyHook economyHook;
    private final File playerDirectory;
    private final Map<String, FishDexMilestone> milestones = new LinkedHashMap<>();
    private final Map<UUID, YamlConfiguration> profiles = new LinkedHashMap<>();
    private final Map<UUID, File> files = new LinkedHashMap<>();

    public FishDexMilestoneManager(
            JavaPlugin plugin,
            PlayerStatsManager statsManager,
            FishRegistry fishRegistry,
            BaitRegistry baitRegistry,
            RodManager rodManager,
            VaultEconomyHook economyHook
    ) {
        this.plugin = plugin;
        this.statsManager = statsManager;
        this.fishRegistry = fishRegistry;
        this.baitRegistry = baitRegistry;
        this.rodManager = rodManager;
        this.economyHook = economyHook;
        this.playerDirectory = new File(plugin.getDataFolder(), "milestones/players");
        if (!playerDirectory.exists() && !playerDirectory.mkdirs()) {
            plugin.getLogger().warning("Could not create FishDex milestone player directory.");
        }
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "milestones.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("milestones");

        milestones.clear();
        if (root == null) {
            plugin.getLogger().warning("milestones.yml does not contain a 'milestones' section.");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            FishDexMilestone.Type type = FishDexMilestone.Type.parse(section.getString("type", "COLLECTION_PERCENT"));
            FishRarity rarity = null;
            String rarityRaw = section.getString("rarity");
            if (rarityRaw != null && !rarityRaw.isBlank()) {
                try {
                    rarity = FishRarity.valueOf(rarityRaw.toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException ex) {
                    plugin.getLogger().warning("Milestone '" + id + "' has invalid rarity '" + rarityRaw + "'.");
                }
            }

            ConfigurationSection reward = section.getConfigurationSection("reward");
            double money = reward == null ? 0.0 : Math.max(0.0, reward.getDouble("money", 0.0));
            String baitId = reward == null ? null : blankToNull(reward.getString("bait-id"));
            int baitAmount = reward == null ? 0 : Math.max(0, reward.getInt("bait-amount", 0));
            int rodXp = reward == null ? 0 : Math.max(0, reward.getInt("rod-xp", 0));
            double luck = reward == null ? 0.0 : Math.max(0.0, reward.getDouble("collection-luck", 0.0));

            FishDexMilestone definition = new FishDexMilestone(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display-name", id),
                    type,
                    clamp(section.getDouble("target-percent", 0.0), 0.0, 100.0),
                    rarity,
                    money,
                    baitId,
                    baitAmount,
                    rodXp,
                    luck
            );
            milestones.put(definition.id(), definition);
        }

        plugin.getLogger().info("Loaded " + milestones.size() + " FishDex milestone definitions.");
    }

    public void recordCatch(Player player, FishDefinition fish, double weight) {
        retryPending(player);
        evaluate(player, true);
    }

    public void evaluate(Player player, boolean notify) {
        retryPending(player);
        YamlConfiguration profile = profile(player.getUniqueId());
        boolean changed = false;

        for (FishDexMilestone milestone : milestones.values()) {
            String claimedPath = "claimed." + milestone.id();
            if (profile.getBoolean(claimedPath, false)) continue;
            if (!qualifies(player, milestone)) continue;

            profile.set(claimedPath, true);
            profile.set("claimed-at." + milestone.id(), System.currentTimeMillis());
            if (milestone.luckBonus() > 0.0) {
                profile.set("collection-luck", profile.getDouble("collection-luck", 0.0) + milestone.luckBonus());
            }
            grantReward(player, profile, milestone);
            changed = true;

            if (notify) {
                player.sendTitle("§b§lFISHDEX MILESTONE!", "§f" + milestone.displayName(), 5, 45, 10);
                player.sendMessage(Component.text("✦ FishDex milestone completed: ", NamedTextColor.AQUA)
                        .append(Component.text(milestone.displayName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
                player.sendMessage(Component.text("Reward: " + rewardSummary(milestone), NamedTextColor.GRAY));
                if (milestone.luckBonus() > 0.0) {
                    player.sendMessage(Component.text(String.format(Locale.US,
                            "Permanent Collection Luck +%.0f%% • Total +%.0f%%",
                            milestone.luckBonus() * 100.0,
                            collectionLuck(player) * 100.0), NamedTextColor.LIGHT_PURPLE));
                }
                player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.1f);
            }
        }

        profile.set("player.name", player.getName());
        profile.set("player.uuid", player.getUniqueId().toString());
        profile.set("last-updated", System.currentTimeMillis());
        if (changed) save(player.getUniqueId(), profile);
    }

    public void sendStatus(Player player) {
        evaluate(player, true);
        int total = fishRegistry.definitions().size();
        int discovered = activeDiscovered(player);
        double percent = total <= 0 ? 0.0 : (discovered * 100.0) / total;

        player.sendMessage(Component.text("━━━━━━━━ FISHDEX MILESTONES ━━━━━━━━", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text(String.format(Locale.US,
                "FishDex: %d/%d discovered (%.1f%%)", discovered, total, percent), NamedTextColor.AQUA));
        player.sendMessage(Component.text(String.format(Locale.US,
                "Permanent Collection Luck: +%.0f%%", collectionLuck(player) * 100.0), NamedTextColor.LIGHT_PURPLE));

        YamlConfiguration profile = profile(player.getUniqueId());
        for (FishDexMilestone milestone : milestones.values()) {
            boolean claimed = profile.getBoolean("claimed." + milestone.id(), false);
            NamedTextColor color = claimed ? NamedTextColor.GREEN : NamedTextColor.GRAY;
            String marker = claimed ? "✔ " : "□ ";
            player.sendMessage(Component.text(marker + milestone.displayName() + " • " + requirementSummary(player, milestone), color));
        }
        player.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_AQUA));
    }

    public double collectionLuck(Player player) {
        if (player == null) return 0.0;
        return Math.max(0.0, profile(player.getUniqueId()).getDouble("collection-luck", 0.0));
    }

    public int definitionCount() {
        return milestones.size();
    }

    public List<FishDexMilestone> definitions() {
        return Collections.unmodifiableList(new ArrayList<>(milestones.values()));
    }

    public void reset(Player player) {
        UUID uuid = player.getUniqueId();
        profiles.remove(uuid);
        File file = file(uuid);
        files.remove(uuid);
        try {
            Files.deleteIfExists(file.toPath());
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not reset FishDex milestone data for " + player.getName() + ": " + ex.getMessage());
        }
    }

    public void shutdown() {
        for (Map.Entry<UUID, YamlConfiguration> entry : profiles.entrySet()) {
            save(entry.getKey(), entry.getValue());
        }
        profiles.clear();
        files.clear();
    }

    private boolean qualifies(Player player, FishDexMilestone milestone) {
        PlayerStatsManager.StatsSnapshot stats = statsManager.snapshot(player);
        return switch (milestone.type()) {
            case COLLECTION_PERCENT -> {
                int total = fishRegistry.definitions().size();
                double percent = total <= 0 ? 0.0 : (activeDiscovered(player) * 100.0) / total;
                yield percent + 0.0001 >= milestone.targetPercent();
            }
            case FIRST_LEGENDARY -> stats.legendaryCatches() > 0;
            case RARITY_COMPLETE -> rarityComplete(player, milestone.rarity());
        };
    }

    private boolean rarityComplete(Player player, FishRarity rarity) {
        if (rarity == null) return false;
        List<FishDefinition> pool = fishRegistry.definitions().values().stream()
                .filter(fish -> fish.rarity() == rarity)
                .toList();
        if (pool.isEmpty()) return false;
        for (FishDefinition fish : pool) {
            if (!statsManager.entry(player, fish.id()).discovered()) return false;
        }
        return true;
    }

    private int activeDiscovered(Player player) {
        int discovered = 0;
        for (FishDefinition fish : fishRegistry.definitions().values()) {
            if (statsManager.entry(player, fish.id()).discovered()) discovered++;
        }
        return discovered;
    }

    private void grantReward(Player player, YamlConfiguration profile, FishDexMilestone milestone) {
        if (milestone.rewardMoney() > 0.0) {
            VaultEconomyHook.DepositResult result = economyHook.deposit(player, milestone.rewardMoney());
            if (!result.success()) {
                profile.set("pending.money", profile.getDouble("pending.money", 0.0) + milestone.rewardMoney());
            }
        }

        if (milestone.rewardBaitId() != null && milestone.rewardBaitAmount() > 0) {
            BaitDefinition bait = baitRegistry.get(milestone.rewardBaitId());
            if (bait != null) giveBait(player, bait, milestone.rewardBaitAmount());
        }

        if (milestone.rewardRodXp() > 0) {
            ItemStack held = player.getInventory().getItemInMainHand();
            if (!rodManager.isProgressionRod(held) || !rodManager.addXp(player, milestone.rewardRodXp())) {
                profile.set("pending.rod-xp", profile.getInt("pending.rod-xp", 0) + milestone.rewardRodXp());
            }
        }
    }

    private void retryPending(Player player) {
        YamlConfiguration profile = profile(player.getUniqueId());
        boolean changed = false;

        double pendingMoney = profile.getDouble("pending.money", 0.0);
        if (pendingMoney > 0.0) {
            VaultEconomyHook.DepositResult result = economyHook.deposit(player, pendingMoney);
            if (result.success()) {
                profile.set("pending.money", 0.0);
                player.sendMessage(Component.text("Pending FishDex reward paid: " + economyHook.format(pendingMoney), NamedTextColor.GREEN));
                changed = true;
            }
        }

        int pendingRodXp = profile.getInt("pending.rod-xp", 0);
        if (pendingRodXp > 0 && rodManager.isProgressionRod(player.getInventory().getItemInMainHand())) {
            if (rodManager.addXp(player, pendingRodXp)) {
                profile.set("pending.rod-xp", 0);
                player.sendMessage(Component.text("Pending FishDex Rod XP claimed: +" + pendingRodXp, NamedTextColor.AQUA));
                changed = true;
            }
        }

        if (changed) save(player.getUniqueId(), profile);
    }

    private void giveBait(Player player, BaitDefinition bait, int amount) {
        int remaining = amount;
        int stackLimit = Math.max(1, bait.material().getMaxStackSize());
        while (remaining > 0) {
            int stack = Math.min(stackLimit, remaining);
            ItemStack item = baitRegistry.createItem(bait, stack);
            Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
            leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
            remaining -= stack;
        }
    }

    private String requirementSummary(Player player, FishDexMilestone milestone) {
        PlayerStatsManager.StatsSnapshot stats = statsManager.snapshot(player);
        return switch (milestone.type()) {
            case COLLECTION_PERCENT -> {
                int total = fishRegistry.definitions().size();
                double percent = total <= 0 ? 0.0 : (activeDiscovered(player) * 100.0) / total;
                yield String.format(Locale.US, "%.1f/%.0f%%", Math.min(percent, milestone.targetPercent()), milestone.targetPercent());
            }
            case FIRST_LEGENDARY -> stats.legendaryCatches() > 0 ? "Legendary discovered" : "Catch your first Legendary";
            case RARITY_COMPLETE -> milestone.rarity() == null ? "Invalid rarity" : "Complete " + milestone.rarity().name() + " collection";
        };
    }

    private String rewardSummary(FishDexMilestone milestone) {
        List<String> rewards = new ArrayList<>();
        if (milestone.rewardMoney() > 0.0) rewards.add(economyHook.format(milestone.rewardMoney()));
        if (milestone.rewardBaitId() != null && milestone.rewardBaitAmount() > 0) {
            BaitDefinition bait = baitRegistry.get(milestone.rewardBaitId());
            rewards.add((bait == null ? milestone.rewardBaitId() : bait.displayName()) + " x" + milestone.rewardBaitAmount());
        }
        if (milestone.rewardRodXp() > 0) rewards.add(milestone.rewardRodXp() + " Rod XP");
        if (milestone.luckBonus() > 0.0) rewards.add(String.format(Locale.US, "+%.0f%% Collection Luck", milestone.luckBonus() * 100.0));
        return rewards.isEmpty() ? "Milestone badge" : String.join(" + ", rewards);
    }

    private YamlConfiguration profile(UUID uuid) {
        return profiles.computeIfAbsent(uuid, ignored -> YamlConfiguration.loadConfiguration(file(uuid)));
    }

    private File file(UUID uuid) {
        return files.computeIfAbsent(uuid, ignored -> new File(playerDirectory, uuid + ".yml"));
    }

    private void save(UUID uuid, YamlConfiguration yaml) {
        try {
            yaml.save(file(uuid));
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save FishDex milestone data for " + uuid + ": " + ex.getMessage());
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
