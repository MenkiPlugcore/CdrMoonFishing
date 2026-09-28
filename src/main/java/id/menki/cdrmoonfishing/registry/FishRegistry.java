package id.menki.cdrmoonfishing.registry;

import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.EncounterPhase;
import id.menki.cdrmoonfishing.model.FishBehavior;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public final class FishRegistry {
    private final JavaPlugin plugin;
    private final Map<String, FishDefinition> definitions = new LinkedHashMap<>();

    public FishRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "fish.yml");
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yaml.getConfigurationSection("fish");

        definitions.clear();
        if (root == null) {
            plugin.getLogger().warning("fish.yml does not contain a 'fish' section.");
            return;
        }

        for (String id : root.getKeys(false)) {
            ConfigurationSection section = root.getConfigurationSection(id);
            if (section == null) continue;

            Material material = Material.matchMaterial(section.getString("material", "COD"));
            if (material == null) {
                plugin.getLogger().warning("Skipping fish '" + id + "': invalid material.");
                continue;
            }

            FishRarity rarity;
            try {
                rarity = FishRarity.valueOf(section.getString("rarity", "COMMON").toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping fish '" + id + "': invalid rarity.");
                continue;
            }

            FishBehavior behavior = parseBehavior(section.getString("behavior", "CALM"), FishBehavior.CALM, id);
            List<EncounterPhase> phases = parsePhases(section, behavior, id);

            FishDefinition definition = new FishDefinition(
                    id,
                    section.getString("display-name", id),
                    material,
                    rarity,
                    behavior,
                    Math.max(0.0, section.getDouble("chance", 1.0)),
                    Math.max(0.01, section.getDouble("min-weight", 0.5)),
                    Math.max(0.01, section.getDouble("max-weight", 3.0)),
                    Math.max(0, section.getInt("min-depth", 0)),
                    Math.max(0, section.getInt("max-depth", 64)),
                    Math.max(0.0, section.getDouble("pull-min", 0.5)),
                    Math.max(0.0, section.getDouble("pull-max", 1.5)),
                    Math.max(0.0, section.getDouble("base-price-per-kg", defaultBasePrice(rarity))),
                    normalize(section.getStringList("biomes"), false),
                    normalize(section.getStringList("weather"), true),
                    normalize(section.getStringList("time"), true),
                    normalize(section.getStringList("required-baits"), false),
                    phases
            );

            definitions.put(id.toLowerCase(Locale.ROOT), sanitize(definition));
        }

        long phased = definitions.values().stream().filter(definition -> !definition.phases().isEmpty()).count();
        plugin.getLogger().info("Loaded " + definitions.size() + " fish definitions (" + phased + " multi-phase).");
    }

    private List<EncounterPhase> parsePhases(ConfigurationSection fishSection, FishBehavior defaultBehavior, String fishId) {
        ConfigurationSection phasesSection = fishSection.getConfigurationSection("phases");
        if (phasesSection == null) return List.of();

        List<EncounterPhase> phases = new ArrayList<>();
        for (String phaseId : phasesSection.getKeys(false)) {
            ConfigurationSection section = phasesSection.getConfigurationSection(phaseId);
            if (section == null) continue;

            FishBehavior behavior = parseBehavior(section.getString("behavior", defaultBehavior.name()), defaultBehavior,
                    fishId + ".phases." + phaseId);

            double startProgress = clamp(section.getDouble("start-progress", 0.0), 0.0, 99.99);
            double pullMultiplier = Math.max(0.1, section.getDouble("pull-multiplier", 1.0));
            double reelPowerMultiplier = Math.max(0.1, section.getDouble("reel-power-multiplier", 1.0));
            double progressMultiplier = Math.max(0.1, section.getDouble("progress-multiplier", 1.0));
            double safeMinOffset = clamp(section.getDouble("safe-min-offset", 0.0), -80.0, 80.0);
            double safeMaxOffset = clamp(section.getDouble("safe-max-offset", 0.0), -80.0, 80.0);

            phases.add(new EncounterPhase(
                    phaseId.toLowerCase(Locale.ROOT),
                    section.getString("display-name", phaseId),
                    startProgress,
                    behavior,
                    pullMultiplier,
                    safeMinOffset,
                    safeMaxOffset,
                    reelPowerMultiplier,
                    progressMultiplier,
                    section.getString("title", ""),
                    section.getString("subtitle", ""),
                    section.getString("sound", "BLOCK_NOTE_BLOCK_PLING")
            ));
        }

        phases.sort(Comparator.comparingDouble(EncounterPhase::startProgress));
        return List.copyOf(phases);
    }

    private FishBehavior parseBehavior(String raw, FishBehavior fallback, String context) {
        try {
            return FishBehavior.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getLogger().warning("Invalid behavior in '" + context + "'; using " + fallback.name() + ".");
            return fallback;
        }
    }

    private FishDefinition sanitize(FishDefinition definition) {
        double minWeight = Math.min(definition.minWeight(), definition.maxWeight());
        double maxWeight = Math.max(definition.minWeight(), definition.maxWeight());
        int minDepth = Math.min(definition.minDepth(), definition.maxDepth());
        int maxDepth = Math.max(definition.minDepth(), definition.maxDepth());
        double pullMin = Math.min(definition.pullMin(), definition.pullMax());
        double pullMax = Math.max(definition.pullMin(), definition.pullMax());

        return new FishDefinition(
                definition.id(), definition.displayName(), definition.material(), definition.rarity(), definition.behavior(),
                definition.chance(), minWeight, maxWeight, minDepth, maxDepth, pullMin, pullMax,
                definition.basePricePerKg(), definition.biomes(), definition.weather(), definition.time(),
                definition.requiredBaits(), definition.phases()
        );
    }

    private List<String> normalize(List<String> input, boolean uppercase) {
        if (input == null || input.isEmpty()) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (String entry : input) {
            if (entry == null || entry.isBlank()) continue;
            String trimmed = entry.trim();
            result.add(uppercase ? trimmed.toUpperCase(Locale.ROOT) : trimmed.toLowerCase(Locale.ROOT));
        }
        return List.copyOf(result);
    }

    public FishDefinition select(String biomeName, int depth, String weatherName, String timeName, BaitDefinition bait) {
        String baitId = bait == null ? null : bait.id();
        List<FishDefinition> eligible = definitions.values().stream()
                .filter(definition -> definition.matches(
                        biomeName.toLowerCase(Locale.ROOT), depth,
                        weatherName.toUpperCase(Locale.ROOT), timeName.toUpperCase(Locale.ROOT), baitId))
                .toList();

        if (eligible.isEmpty()) return null;

        double total = eligible.stream().mapToDouble(definition -> adjustedWeight(definition, bait)).sum();
        if (total <= 0.0) return null;

        double roll = ThreadLocalRandom.current().nextDouble(total);
        double cursor = 0.0;
        for (FishDefinition definition : eligible) {
            cursor += adjustedWeight(definition, bait);
            if (roll <= cursor) return definition;
        }
        return eligible.get(eligible.size() - 1);
    }

    private double adjustedWeight(FishDefinition definition, BaitDefinition bait) {
        double multiplier = bait == null ? 1.0 : bait.multiplierFor(definition);
        return Math.max(0.0, definition.chance() * multiplier);
    }

    public FishDefinition get(String id) {
        if (id == null) return null;
        return definitions.get(id.toLowerCase(Locale.ROOT));
    }

    public Map<String, FishDefinition> definitions() {
        return Collections.unmodifiableMap(definitions);
    }

    private double defaultBasePrice(FishRarity rarity) {
        return switch (rarity) {
            case COMMON -> 10.0;
            case UNCOMMON -> 15.0;
            case RARE -> 30.0;
            case EPIC -> 60.0;
            case LEGENDARY -> 150.0;
        };
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
