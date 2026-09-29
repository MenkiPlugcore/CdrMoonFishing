package id.menki.cdrmoonfishing.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Adds newly bundled fish definitions to existing installations without
 * overwriting server-owner edits to already existing species.
 */
public final class FishContentMigration {
    private FishContentMigration() {
    }

    public static void apply(JavaPlugin plugin) {
        File file = new File(plugin.getDataFolder(), "fish.yml");
        if (!file.exists()) return;

        InputStream stream = plugin.getResource("fish.yml");
        if (stream == null) return;

        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        ConfigurationSection bundledFish = bundled.getConfigurationSection("fish");
        if (bundledFish == null) return;

        int added = 0;
        for (String id : bundledFish.getKeys(false)) {
            String basePath = "fish." + id;
            if (live.isConfigurationSection(basePath)) continue;

            ConfigurationSection source = bundledFish.getConfigurationSection(id);
            if (source == null) continue;

            live.createSection(basePath);
            for (Map.Entry<String, Object> entry : source.getValues(true).entrySet()) {
                if (entry.getValue() instanceof ConfigurationSection) continue;
                live.set(basePath + "." + entry.getKey(), entry.getValue());
            }
            added++;
        }

        if (added <= 0) return;
        try {
            live.save(file);
            plugin.getLogger().info("Added " + added + " new default fish definitions to fish.yml without overwriting existing species.");
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not migrate new fish definitions: " + ex.getMessage());
        }
    }
}
