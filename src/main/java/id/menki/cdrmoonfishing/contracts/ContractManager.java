package id.menki.cdrmoonfishing.contracts;

import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook.DepositResult;
import id.menki.cdrmoonfishing.fishing.FishingSession;
import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.registry.BaitRegistry;
import id.menki.cdrmoonfishing.rod.RodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

public final class ContractManager {
    private final JavaPlugin plugin;
    private final VaultEconomyHook economy;
    private final BaitRegistry baitRegistry;
    private final RodManager rodManager;
    private final File playerDirectory;
    private final Map<String, ContractDefinition> definitions = new LinkedHashMap<>();

    private int dailyCount = 3;
    private ZoneId zoneId = ZoneId.of("Asia/Jakarta");

    public ContractManager(JavaPlugin plugin, VaultEconomyHook economy, BaitRegistry baitRegistry, RodManager rodManager) {
        this.plugin = plugin;
        this.economy = economy;
        this.baitRegistry = baitRegistry;
        this.rodManager = rodManager;
        this.playerDirectory = new File(plugin.getDataFolder(), "contracts/players");
        if (!playerDirectory.exists() && !playerDirectory.mkdirs()) plugin.getLogger().warning("Could not create daily contract player directory.");
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "contracts.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);

        dailyCount = Math.max(1, yaml.getInt("settings.daily-count", 3));
        String timezone = yaml.getString("settings.timezone", "Asia/Jakarta");
        try { zoneId = ZoneId.of(timezone); }
        catch (Exception ex) {
            zoneId = ZoneId.of("Asia/Jakarta");
            plugin.getLogger().warning("Invalid contracts timezone '" + timezone + "'; using Asia/Jakarta.");
        }

        definitions.clear();
        ConfigurationSection root = yaml.getConfigurationSection("contracts");
        if (root == null) {
            plugin.getLogger().warning("contracts.yml does not contain a 'contracts' section.");
            return;
        }

        for (String rawId : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(rawId);
            if (section == null) continue;
            String id = rawId.toLowerCase(Locale.ROOT);
            double target = Math.max(0.01, section.getDouble("target", 1.0));
            ContractDefinition.ProgressMode progressMode = ContractDefinition.ProgressMode.parse(section.getString("progress-mode", "COUNT"));
            FishRarity minRarity = parseRarity(section.getString("conditions.min-rarity"), id);
            ConfigurationSection rewards = section.getConfigurationSection("rewards");
            double money = rewards == null ? 0.0 : Math.max(0.0, rewards.getDouble("money", 0.0));
            int rodXp = rewards == null ? 0 : Math.max(0, rewards.getInt("rod-xp", 0));
            String rewardBaitId = rewards == null ? null : blankToNull(rewards.getString("bait.id"));
            int rewardBaitAmount = rewards == null ? 0 : Math.max(0, rewards.getInt("bait.amount", 0));

            ContractDefinition definition = new ContractDefinition(
                    id,
                    section.getString("display-name", rawId),
                    section.getString("description", "Selesaikan target memancing."),
                    progressMode,
                    target,
                    blankToNull(section.getString("conditions.fish-id")),
                    minRarity,
                    blankToNull(section.getString("conditions.bait-id")),
                    section.getInt("conditions.min-depth", -1),
                    section.getInt("conditions.max-depth", -1),
                    upperOrNull(section.getString("conditions.time")),
                    upperOrNull(section.getString("conditions.weather")),
                    money,
                    rewardBaitId,
                    rewardBaitAmount,
                    rodXp
            );
            definitions.put(id, definition);
        }
        plugin.getLogger().info("Loaded " + definitions.size() + " daily fishing contracts; " + Math.min(dailyCount, definitions.size()) + " selected per day.");
    }

    public void recordCatch(Player player, FishDefinition fish, double weight, FishingSession session) {
        if (definitions.isEmpty()) return;
        YamlConfiguration profile = profile(player.getUniqueId());
        ensureDate(profile, player.getUniqueId());
        retryPendingMoney(player, profile);

        boolean changed = false;
        for (ContractDefinition definition : todayContracts()) {
            String id = definition.id();
            if (profile.getBoolean("completed." + id, false)) continue;
            if (!definition.matches(fish, session)) continue;
            double before = profile.getDouble("progress." + id, 0.0);
            double after = Math.min(definition.target(), before + definition.progressAmount(weight));
            profile.set("progress." + id, after);
            changed = true;

            if (after + 0.0001 >= definition.target()) {
                profile.set("completed." + id, true);
                save(player.getUniqueId(), profile);
                grantRewards(player, definition, profile);
                player.sendMessage(Component.text("✓ Kontrak Harian Selesai: ", NamedTextColor.GREEN)
                        .append(Component.text(definition.displayName(), NamedTextColor.AQUA)));
                player.sendMessage(Component.text("Hadiah: " + rewardSummary(definition), NamedTextColor.GRAY));
                player.sendTitle("§a§lKONTRAK SELESAI", "§f" + definition.displayName(), 5, 35, 10);
            }
        }
        if (changed) save(player.getUniqueId(), profile);
    }

    public List<ContractProgress> progress(Player player) {
        YamlConfiguration profile = profile(player.getUniqueId());
        ensureDate(profile, player.getUniqueId());
        retryPendingMoney(player, profile);
        List<ContractProgress> result = new ArrayList<>();
        for (ContractDefinition definition : todayContracts()) {
            double value = Math.min(definition.target(), profile.getDouble("progress." + definition.id(), 0.0));
            boolean completed = profile.getBoolean("completed." + definition.id(), false);
            result.add(new ContractProgress(definition, value, completed));
        }
        return List.copyOf(result);
    }

    public List<ContractDefinition> todayContracts() {
        if (definitions.isEmpty()) return List.of();
        List<ContractDefinition> list = new ArrayList<>(definitions.values());
        list.sort(Comparator.comparing(ContractDefinition::id));
        LocalDate date = LocalDate.now(zoneId);
        long seed = (date.toEpochDay() * 1_000_003L) ^ 0x4D4F4F4E46495348L;
        Collections.shuffle(list, new Random(seed));
        return List.copyOf(list.subList(0, Math.min(dailyCount, list.size())));
    }

    public void reset(Player player) {
        YamlConfiguration profile = profile(player.getUniqueId());
        profile.set("date", currentDate());
        profile.set("progress", null);
        profile.set("completed", null);
        save(player.getUniqueId(), profile);
    }

    public String currentDate() { return LocalDate.now(zoneId).format(DateTimeFormatter.ISO_LOCAL_DATE); }

    public long millisUntilReset() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = now.toLocalDate().plusDays(1).atStartOfDay(zoneId);
        return Math.max(0L, Duration.between(now, next).toMillis());
    }

    public String rewardSummary(ContractDefinition definition) {
        List<String> rewards = new ArrayList<>();
        if (definition.rewardMoney() > 0.0) rewards.add(economy.format(definition.rewardMoney()));
        if (definition.rewardBaitId() != null && definition.rewardBaitAmount() > 0) {
            BaitDefinition bait = baitRegistry.get(definition.rewardBaitId());
            rewards.add((bait == null ? definition.rewardBaitId() : bait.displayName()) + " x" + definition.rewardBaitAmount());
        }
        if (definition.rewardRodXp() > 0) rewards.add(definition.rewardRodXp() + " XP Joran");
        return rewards.isEmpty() ? "Tidak ada hadiah" : String.join(" + ", rewards);
    }

    public ZoneId zoneId() { return zoneId; }
    public int definitionCount() { return definitions.size(); }

    private void grantRewards(Player player, ContractDefinition definition, YamlConfiguration profile) {
        if (definition.rewardMoney() > 0.0) {
            DepositResult result = economy.deposit(player, definition.rewardMoney());
            if (!result.success()) {
                double pending = profile.getDouble("pending-money", 0.0) + definition.rewardMoney();
                profile.set("pending-money", pending);
                plugin.getLogger().warning("Queued daily contract money for " + player.getName() + ": " + economy.format(definition.rewardMoney()) + " (" + result.error() + ")");
            }
        }

        if (definition.rewardBaitId() != null && definition.rewardBaitAmount() > 0) {
            BaitDefinition bait = baitRegistry.get(definition.rewardBaitId());
            if (bait != null) {
                int remaining = definition.rewardBaitAmount();
                while (remaining > 0) {
                    int amount = Math.min(remaining, bait.material().getMaxStackSize());
                    ItemStack item = baitRegistry.createItem(bait, amount);
                    Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
                    leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
                    remaining -= amount;
                }
            } else plugin.getLogger().warning("Contract reward references unknown bait '" + definition.rewardBaitId() + "'.");
        }

        if (definition.rewardRodXp() > 0) rodManager.addXp(player, definition.rewardRodXp());
    }

    private void retryPendingMoney(Player player, YamlConfiguration profile) {
        double pending = profile.getDouble("pending-money", 0.0);
        if (pending <= 0.0) return;
        DepositResult result = economy.deposit(player, pending);
        if (result.success()) {
            profile.set("pending-money", 0.0);
            save(player.getUniqueId(), profile);
            player.sendMessage(Component.text("Hadiah kontrak memancing tertunda diterima: " + economy.format(pending), NamedTextColor.GREEN));
        }
    }

    private void ensureDate(YamlConfiguration profile, UUID uuid) {
        String today = currentDate();
        if (today.equals(profile.getString("date"))) return;
        profile.set("date", today);
        profile.set("progress", null);
        profile.set("completed", null);
        save(uuid, profile);
    }

    private YamlConfiguration profile(UUID uuid) { return YamlConfiguration.loadConfiguration(file(uuid)); }
    private File file(UUID uuid) { return new File(playerDirectory, uuid + ".yml"); }

    private void save(UUID uuid, YamlConfiguration yaml) {
        try { yaml.save(file(uuid)); }
        catch (IOException ex) { plugin.getLogger().severe("Could not save daily contract profile for " + uuid + ": " + ex.getMessage()); }
    }

    private FishRarity parseRarity(String raw, String contractId) {
        if (raw == null || raw.isBlank()) return null;
        try { return FishRarity.valueOf(raw.trim().toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Contract '" + contractId + "' has invalid min-rarity '" + raw + "'.");
            return null;
        }
    }

    private String blankToNull(String value) {
        if (value == null || value.isBlank()) return null;
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private String upperOrNull(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("ANY")) return null;
        return value.trim().toUpperCase(Locale.ROOT);
    }

    public record ContractProgress(ContractDefinition definition, double progress, boolean completed) {}
}
