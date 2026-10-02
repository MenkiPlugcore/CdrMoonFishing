package id.menki.cdrmoonfishing.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;

/**
 * Synchronizes bundled YAML defaults into an existing installation without
 * replacing administrator-owned values.
 *
 * Rules:
 * - missing files are created from the JAR;
 * - missing keys are copied from the bundled resource;
 * - existing values are never overwritten;
 * - config-version is advanced to the bundled schema version;
 * - a pre-sync backup is created once per plugin version before a file changes.
 *
 * Runtime/state files are intentionally excluded.
 */
public final class BundledYamlSynchronizer {
    private static final List<String> BUNDLED_FILES = List.of(
            "config.yml",
            "fish.yml",
            "bait.yml",
            "rod.yml",
            "contracts.yml",
            "milestones.yml",
            "supply.yml"
    );

    private BundledYamlSynchronizer() { }

    public static SyncSummary syncAll(JavaPlugin plugin) {
        int createdFiles = 0;
        int updatedFiles = 0;
        int addedKeys = 0;

        if (!plugin.getDataFolder().exists() && !plugin.getDataFolder().mkdirs()) {
            plugin.getLogger().warning("Could not create plugin data folder before YAML synchronization.");
        }

        for (String fileName : BUNDLED_FILES) {
            SyncResult result = syncFile(plugin, fileName);
            if (result.created()) createdFiles++;
            if (result.updated()) updatedFiles++;
            addedKeys += result.addedKeys();
        }

        SyncSummary summary = new SyncSummary(createdFiles, updatedFiles, addedKeys);
        if (summary.changed()) {
            plugin.getLogger().info("Auto-sync YAML selesai: " + createdFiles + " file dibuat, "
                    + updatedFiles + " file diperbarui, " + addedKeys + " key baru ditambahkan."
                    + " Nilai custom tetap dipertahankan.");
        } else {
            plugin.getLogger().info("Auto-sync YAML: semua file sudah sinkron dengan bundled defaults.");
        }
        return summary;
    }

    private static SyncResult syncFile(JavaPlugin plugin, String fileName) {
        File file = new File(plugin.getDataFolder(), fileName);
        boolean created = false;

        if (!file.isFile()) {
            try {
                if (fileName.equals("config.yml")) plugin.saveDefaultConfig();
                else plugin.saveResource(fileName, false);
                created = file.isFile();
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Bundled resource " + fileName + " tidak ditemukan: " + ex.getMessage());
                return new SyncResult(false, false, 0);
            }
        }

        InputStream stream = plugin.getResource(fileName);
        if (stream == null || !file.isFile()) return new SyncResult(created, false, 0);

        YamlConfiguration live = YamlConfiguration.loadConfiguration(file);
        YamlConfiguration bundled = YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));

        int added = 0;
        for (Map.Entry<String, Object> entry : bundled.getValues(true).entrySet()) {
            String path = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof ConfigurationSection) continue;
            if (live.contains(path)) continue;
            live.set(path, value);
            added++;
        }

        // config-version is schema metadata, not a gameplay/admin preference.
        // Advancing it makes migrations deterministic while all other existing
        // values remain untouched.
        if (fileName.equals("config.yml") && bundled.contains("config-version")) {
            int bundledVersion = bundled.getInt("config-version", 1);
            int liveVersion = live.getInt("config-version", 0);
            if (bundledVersion > liveVersion) {
                live.set("config-version", bundledVersion);
                added++;
            }
        }

        if (added <= 0) return new SyncResult(created, false, 0);

        createBackup(plugin, file, fileName);
        try {
            live.save(file);
            plugin.getLogger().info("Synced " + fileName + ": +" + added + " missing key(s), existing values preserved.");
            return new SyncResult(created, true, added);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save synchronized " + fileName + ": " + ex.getMessage());
            return new SyncResult(created, false, 0);
        }
    }

    private static void createBackup(JavaPlugin plugin, File source, String fileName) {
        if (!source.isFile()) return;
        String version = plugin.getPluginMeta().getVersion().replaceAll("[^A-Za-z0-9._-]", "_");
        File backupDir = new File(plugin.getDataFolder(), "sync-backups/" + version);
        File backup = new File(backupDir, fileName + ".bak");
        if (backup.exists()) return;

        try {
            Files.createDirectories(backupDir.toPath());
            Files.copy(source.toPath(), backup.toPath(), StandardCopyOption.COPY_ATTRIBUTES);
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not create pre-sync backup for " + fileName + ": " + ex.getMessage());
        }
    }

    private record SyncResult(boolean created, boolean updated, int addedKeys) { }

    public record SyncSummary(int createdFiles, int updatedFiles, int addedKeys) {
        public boolean changed() {
            return createdFiles > 0 || updatedFiles > 0 || addedKeys > 0;
        }
    }
}
