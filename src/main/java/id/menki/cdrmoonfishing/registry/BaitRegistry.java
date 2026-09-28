package id.menki.cdrmoonfishing.registry;

import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class BaitRegistry {
    private final JavaPlugin plugin;
    private final Map<String, BaitDefinition> definitions = new LinkedHashMap<>();
    private final NamespacedKey baitIdKey;

    public BaitRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.baitIdKey = new NamespacedKey(plugin, "bait_id");
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "bait.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("bait");

        definitions.clear();
        if (root == null) {
            plugin.getLogger().warning("bait.yml does not contain a 'bait' section.");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) {
                continue;
            }

            Material material = Material.matchMaterial(section.getString("material", "STRING"));
            if (material == null) {
                plugin.getLogger().warning("Skipping bait '" + id + "': invalid material.");
                continue;
            }

            Map<FishRarity, Double> rarityMultipliers = new LinkedHashMap<>();
            ConfigurationSection raritySection = section.getConfigurationSection("rarity-multipliers");
            if (raritySection != null) {
                for (String rarityName : raritySection.getKeys(false)) {
                    try {
                        FishRarity rarity = FishRarity.valueOf(rarityName.toUpperCase(Locale.ROOT));
                        rarityMultipliers.put(rarity, Math.max(0.0, raritySection.getDouble(rarityName, 1.0)));
                    } catch (IllegalArgumentException ex) {
                        plugin.getLogger().warning("Ignoring invalid rarity multiplier '" + rarityName + "' in bait '" + id + "'.");
                    }
                }
            }

            Map<String, Double> fishMultipliers = new LinkedHashMap<>();
            ConfigurationSection fishSection = section.getConfigurationSection("fish-multipliers");
            if (fishSection != null) {
                for (String fishId : fishSection.getKeys(false)) {
                    fishMultipliers.put(fishId.toLowerCase(Locale.ROOT), Math.max(0.0, fishSection.getDouble(fishId, 1.0)));
                }
            }

            BaitDefinition definition = new BaitDefinition(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display-name", id),
                    material,
                    Map.copyOf(rarityMultipliers),
                    Map.copyOf(fishMultipliers)
            );
            definitions.put(definition.id(), definition);
        }

        plugin.getLogger().info("Loaded " + definitions.size() + " bait definitions.");
    }

    public BaitDefinition get(String id) {
        if (id == null) {
            return null;
        }
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public BaitDefinition identify(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        String id = item.getItemMeta().getPersistentDataContainer().get(baitIdKey, PersistentDataType.STRING);
        return get(id);
    }

    public ItemStack createItem(BaitDefinition bait, int amount) {
        int safeAmount = Math.max(1, Math.min(amount, bait.material().getMaxStackSize()));
        ItemStack item = new ItemStack(bait.material(), safeAmount);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(bait.displayName(), NamedTextColor.GOLD)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(java.util.List.of(
                Component.text("Fishing Bait", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click to select", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Consumed when a fish bites", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)
        ));
        meta.getPersistentDataContainer().set(baitIdKey, PersistentDataType.STRING, bait.id());
        item.setItemMeta(meta);
        return item;
    }

    public int count(Player player, BaitDefinition bait) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            BaitDefinition found = identify(item);
            if (found != null && found.id().equals(bait.id())) {
                count += item.getAmount();
            }
        }
        return count;
    }

    public boolean consumeOne(Player player, BaitDefinition bait) {
        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            BaitDefinition found = identify(item);
            if (found == null || !found.id().equals(bait.id())) {
                continue;
            }

            if (item.getAmount() <= 1) {
                inventory.setItem(slot, null);
            } else {
                item.setAmount(item.getAmount() - 1);
                inventory.setItem(slot, item);
            }
            return true;
        }
        return false;
    }

    public Map<String, BaitDefinition> definitions() {
        return Collections.unmodifiableMap(definitions);
    }
}
