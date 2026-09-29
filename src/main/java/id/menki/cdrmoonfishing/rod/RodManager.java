package id.menki.cdrmoonfishing.rod;

import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.RodTierDefinition;
import id.menki.cdrmoonfishing.registry.RodRegistry;
import id.menki.cdrmoonfishing.ui.FishingUiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

/**
 * Tier-based fishing rod manager.
 *
 * Rod XP was removed in v1.0.8. v1.0.9 maps every tier to a native 1.21.11
 * minecraft:item_model entry (cdrmoonfishing:rod/<tier>) so custom visuals do
 * not consume or collide with numeric CustomModelData used by other plugins.
 */
public final class RodManager implements Listener {
    private static final int HUB_ROD_SLOT = 11;

    private final JavaPlugin plugin;
    private final RodRegistry registry;
    private final NamespacedKey rodIdKey;
    private final NamespacedKey legacyRodXpKey;
    private final NamespacedKey rodTierKey;
    private ToDoubleFunction<Player> collectionLuckProvider = player -> 0.0;

    public RodManager(JavaPlugin plugin, RodRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.rodIdKey = new NamespacedKey(plugin, "rod_id");
        this.legacyRodXpKey = new NamespacedKey(plugin, "rod_xp");
        this.rodTierKey = new NamespacedKey(plugin, "rod_tier");

        removeLegacyXpConfiguration();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public ItemStack createRod(String tierId) {
        RodTierDefinition tier = registry.get(tierId);
        if (tier == null) tier = registry.firstTier();
        if (tier == null) return new ItemStack(Material.FISHING_ROD);

        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.getPersistentDataContainer().set(rodIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, tier.id());
        meta.setItemModel(itemModelKey(tier));
        rod.setItemMeta(meta);
        refreshMeta(rod);
        return rod;
    }

    public boolean isProgressionRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(rodIdKey, PersistentDataType.STRING);
    }

    public RodTierDefinition tier(ItemStack item) {
        if (!isProgressionRod(item)) return null;
        stripLegacyXp(item);

        ItemMeta meta = item.getItemMeta();
        String stored = meta.getPersistentDataContainer().get(rodTierKey, PersistentDataType.STRING);
        RodTierDefinition tier = registry.get(stored);
        if (tier != null) {
            syncVisualModel(item, meta, tier);
            return tier;
        }

        RodTierDefinition fallback = registry.firstTier();
        if (fallback != null) {
            meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, fallback.id());
            meta.setItemModel(itemModelKey(fallback));
            item.setItemMeta(meta);
        }
        return fallback;
    }

    /** Legacy compatibility method. Rod XP no longer exists. */
    public int xp(ItemStack item) {
        stripLegacyXp(item);
        return 0;
    }

    public double reelMultiplier(Player player) {
        RodTierDefinition tier = tier(player.getInventory().getItemInMainHand());
        return tier == null ? 1.0 : tier.reelMultiplier();
    }

    public double rarityMultiplier(Player player, FishDefinition fish) {
        RodTierDefinition tier = tier(player.getInventory().getItemInMainHand());
        double rodLuck = tier == null ? 0.0 : tier.rarityLuck();
        double collectionLuck = Math.max(0.0, collectionLuckProvider.applyAsDouble(player));
        double luck = rodLuck + collectionLuck;

        return switch (fish.rarity()) {
            case COMMON -> 1.0;
            case UNCOMMON -> 1.0 + (luck * 0.50);
            case RARE -> 1.0 + luck;
            case EPIC -> 1.0 + (luck * 1.50);
            case LEGENDARY -> 1.0 + (luck * 2.00);
        };
    }

    public void setCollectionLuckProvider(ToDoubleFunction<Player> provider) {
        this.collectionLuckProvider = provider == null ? player -> 0.0 : provider;
    }

    /** Catching fish no longer awards rod XP and can never auto-upgrade a rod. */
    public void recordCatch(Player player, FishDefinition fish, double weight) {
        // Intentionally empty: rod progression is no longer XP based.
    }

    /** Legacy compatibility hook. */
    public boolean addXp(Player player, int amount) {
        if (player != null) stripLegacyXp(player.getInventory().getItemInMainHand());
        return amount > 0;
    }

    public boolean setTier(ItemStack rod, String tierId) {
        if (!isProgressionRod(rod)) return false;
        RodTierDefinition tier = registry.get(tierId);
        if (tier == null) return false;

        ItemMeta meta = rod.getItemMeta();
        meta.getPersistentDataContainer().remove(legacyRodXpKey);
        meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, tier.id());
        meta.setItemModel(itemModelKey(tier));
        rod.setItemMeta(meta);
        refreshMeta(rod);
        return true;
    }

    public void refreshMeta(ItemStack rod) {
        if (!isProgressionRod(rod)) return;
        RodTierDefinition tier = tier(rod);
        if (tier == null) return;

        ItemMeta meta = rod.getItemMeta();
        meta.getPersistentDataContainer().remove(legacyRodXpKey);
        meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, tier.id());
        meta.setItemModel(itemModelKey(tier));
        meta.displayName(Component.text(tier.displayName(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Joran CdrMoonFishing", NamedTextColor.DARK_AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("Tingkat: ", NamedTextColor.GRAY)
                .append(Component.text(tier.displayName(), NamedTextColor.AQUA)));
        lore.add(Component.text(String.format(Locale.US, "Kekuatan Tarik: %.2fx", tier.reelMultiplier()), NamedTextColor.GRAY));
        lore.add(Component.text(String.format(Locale.US, "Luck Kelangkaan: +%.0f%%", tier.rarityLuck() * 100.0), NamedTextColor.GRAY));
        meta.lore(lore);
        rod.setItemMeta(meta);
    }

    public RodRegistry registry() {
        return registry;
    }

    @EventHandler
    public void onHubOpen(InventoryOpenEvent event) {
        if (!(event.getPlayer() instanceof Player player)) return;
        if (!(event.getInventory().getHolder() instanceof FishingUiManager.HubHolder)) return;

        ItemStack button = event.getInventory().getItem(HUB_ROD_SLOT);
        if (button == null || button.getType().isAir()) return;

        ItemMeta meta = button.getItemMeta();
        ItemStack heldRod = player.getInventory().getItemInMainHand();
        RodTierDefinition tier = tier(heldRod);
        List<Component> lore = new ArrayList<>();

        if (tier == null) {
            lore.add(Component.text("Tidak memegang joran CdrMoonFishing", NamedTextColor.GRAY));
            lore.add(Component.text("Pegang joran di tangan utama", NamedTextColor.DARK_GRAY));
        } else {
            lore.add(Component.text("Tingkat: " + tier.displayName(), NamedTextColor.AQUA));
            lore.add(Component.text(String.format(Locale.US, "Luck +%.0f%% • Tarik %.2fx",
                    tier.rarityLuck() * 100.0, tier.reelMultiplier()), NamedTextColor.LIGHT_PURPLE));
        }
        lore.add(Component.text("Klik untuk detail joran", NamedTextColor.DARK_GRAY));
        meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        button.setItemMeta(meta);
        event.getInventory().setItem(HUB_ROD_SLOT, button);
    }

    private NamespacedKey itemModelKey(RodTierDefinition tier) {
        return new NamespacedKey(plugin, "rod/" + tier.id());
    }

    private void syncVisualModel(ItemStack item, ItemMeta meta, RodTierDefinition tier) {
        NamespacedKey expected = itemModelKey(tier);
        if (expected.equals(meta.getItemModel())) return;
        meta.setItemModel(expected);
        item.setItemMeta(meta);
    }

    private void stripLegacyXp(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (!meta.getPersistentDataContainer().has(legacyRodXpKey, PersistentDataType.INTEGER)) return;
        meta.getPersistentDataContainer().remove(legacyRodXpKey);
        item.setItemMeta(meta);
    }

    private void removeLegacyXpConfiguration() {
        cleanRodConfig(new File(plugin.getDataFolder(), "rod.yml"));
        cleanRewardXp(new File(plugin.getDataFolder(), "contracts.yml"), "contracts", "rewards.rod-xp");
        cleanRewardXp(new File(plugin.getDataFolder(), "milestones.yml"), "milestones", "reward.rod-xp");
        cleanPendingMilestoneXp(new File(plugin.getDataFolder(), "milestones/players"));
    }

    private void cleanRodConfig(File file) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        boolean changed = false;

        if (yaml.contains("progression")) {
            yaml.set("progression", null);
            changed = true;
        }

        ConfigurationSection tiers = yaml.getConfigurationSection("tiers");
        if (tiers != null) {
            for (String id : tiers.getKeys(false)) {
                String minXp = "tiers." + id + ".min-xp";
                String xpMultiplier = "tiers." + id + ".xp-multiplier";
                if (yaml.contains(minXp)) {
                    yaml.set(minXp, null);
                    changed = true;
                }
                if (yaml.contains(xpMultiplier)) {
                    yaml.set(xpMultiplier, null);
                    changed = true;
                }
            }
        }
        saveYaml(file, yaml, changed);
    }

    private void cleanRewardXp(File file, String rootPath, String relativePath) {
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection(rootPath);
        if (root == null) return;

        boolean changed = false;
        for (String id : root.getKeys(false)) {
            String path = rootPath + "." + id + "." + relativePath;
            if (!yaml.contains(path)) continue;
            yaml.set(path, null);
            changed = true;
        }
        saveYaml(file, yaml, changed);
    }

    private void cleanPendingMilestoneXp(File directory) {
        if (!directory.isDirectory()) return;
        File[] files = directory.listFiles((dir, name) -> name.endsWith(".yml"));
        if (files == null) return;

        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            if (!yaml.contains("pending.rod-xp")) continue;
            yaml.set("pending.rod-xp", null);
            saveYaml(file, yaml, true);
        }
    }

    private void saveYaml(File file, YamlConfiguration yaml, boolean changed) {
        if (!changed) return;
        try {
            yaml.save(file);
            plugin.getLogger().info("Removed legacy rod XP data from " + file.getName() + ".");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not remove legacy rod XP data from " + file.getName() + ": " + ex.getMessage());
        }
    }
}
