package id.menki.cdrmoonfishing.integration;

import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.LinkedHashMap;
import java.util.Map;

public final class IntegrationManager {
    private final JavaPlugin plugin;

    public IntegrationManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public Map<String, Boolean> status() {
        PluginManager manager = plugin.getServer().getPluginManager();
        Map<String, Boolean> result = new LinkedHashMap<>();
        result.put("Vault", manager.isPluginEnabled("Vault"));
        result.put("ItemsAdder", manager.isPluginEnabled("ItemsAdder"));
        result.put("MMOItems", manager.isPluginEnabled("MMOItems"));
        result.put("Floodgate", manager.isPluginEnabled("floodgate"));
        result.put("Geyser", manager.isPluginEnabled("Geyser-Spigot"));
        return result;
    }

    public void logStatus() {
        status().forEach((name, enabled) ->
                plugin.getLogger().info("Integration " + name + ": " + (enabled ? "detected" : "not detected")));
    }
}
