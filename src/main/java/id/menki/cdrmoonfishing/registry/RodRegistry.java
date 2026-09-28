package id.menki.cdrmoonfishing.registry;

import id.menki.cdrmoonfishing.model.RodTierDefinition;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class RodRegistry {
    private final JavaPlugin plugin;
    private final Map<String, RodTierDefinition> tiersById = new LinkedHashMap<>();
    private List<RodTierDefinition> orderedTiers = List.of();
    private YamlConfiguration config = new YamlConfiguration();

    public RodRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "rod.yml");
        this.config = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = config.getConfigurationSection("tiers");

        tiersById.clear();
        if (root == null) {
            plugin.getLogger().warning("rod.yml does not contain a 'tiers' section.");
            orderedTiers = List.of();
            return;
        }

        List<RodTierDefinition> loaded = new ArrayList<>();
        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            RodTierDefinition tier = new RodTierDefinition(
                    id.toLowerCase(Locale.ROOT),
                    section.getString("display-name", id),
                    Math.max(0, section.getInt("min-xp", 0)),
                    Math.max(0.1, section.getDouble("reel-multiplier", 1.0)),
                    Math.max(0.0, section.getDouble("rarity-luck", 0.0)),
                    Math.max(0.1, section.getDouble("xp-multiplier", 1.0))
            );
            loaded.add(tier);
        }

        loaded.sort(Comparator.comparingInt(RodTierDefinition::minXp));
        for (RodTierDefinition tier : loaded) {
            tiersById.put(tier.id(), tier);
        }
        orderedTiers = List.copyOf(loaded);
        plugin.getLogger().info("Loaded " + orderedTiers.size() + " fishing rod tiers.");
    }

    public RodTierDefinition get(String id) {
        if (id == null) return null;
        return tiersById.get(id.toLowerCase(Locale.ROOT));
    }

    public RodTierDefinition firstTier() {
        return orderedTiers.isEmpty() ? null : orderedTiers.getFirst();
    }

    public RodTierDefinition tierForXp(int xp) {
        RodTierDefinition current = firstTier();
        for (RodTierDefinition tier : orderedTiers) {
            if (xp < tier.minXp()) break;
            current = tier;
        }
        return current;
    }

    public RodTierDefinition nextTier(RodTierDefinition current) {
        if (current == null) return firstTier();
        for (int i = 0; i < orderedTiers.size(); i++) {
            if (!orderedTiers.get(i).id().equals(current.id())) continue;
            return i + 1 < orderedTiers.size() ? orderedTiers.get(i + 1) : null;
        }
        return null;
    }

    public List<RodTierDefinition> tiers() {
        return orderedTiers;
    }

    public int baseXp(String rarityName, int fallback) {
        return Math.max(0, config.getInt("progression.base-xp." + rarityName, fallback));
    }

    public double weightXpMultiplier() {
        return Math.max(0.0, config.getDouble("progression.weight-xp-multiplier", 0.5));
    }
}
