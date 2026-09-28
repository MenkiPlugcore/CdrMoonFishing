package id.menki.cdrmoonfishing.market;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public final class SoldCatchLedger {
    private final JavaPlugin plugin;
    private final File file;
    private final Set<String> sold = new HashSet<>();
    private YamlConfiguration yaml;

    public SoldCatchLedger(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "sold-catches.yml");
        reload();
    }

    public void reload() {
        this.yaml = YamlConfiguration.loadConfiguration(file);
        sold.clear();
        sold.addAll(yaml.getStringList("sold"));
    }

    public boolean isSold(String catchUid) {
        return catchUid != null && sold.contains(catchUid);
    }

    public boolean markSold(String catchUid) {
        if (catchUid == null || catchUid.isBlank() || !sold.add(catchUid)) return false;
        if (persist()) return true;
        sold.remove(catchUid);
        persistQuietly();
        return false;
    }

    public boolean unmarkSold(String catchUid) {
        if (catchUid == null || !sold.remove(catchUid)) return false;
        if (persist()) return true;
        sold.add(catchUid);
        persistQuietly();
        return false;
    }

    public int size() {
        return sold.size();
    }

    private boolean persist() {
        yaml.set("sold", sold.stream().sorted().toList());
        try {
            yaml.save(file);
            return true;
        } catch (IOException ex) {
            plugin.getLogger().severe("Could not save sold catch ledger: " + ex.getMessage());
            return false;
        }
    }

    private void persistQuietly() {
        yaml.set("sold", sold.stream().sorted().toList());
        try {
            yaml.save(file);
        } catch (IOException ignored) {
            // Primary failure is already logged by persist().
        }
    }
}
