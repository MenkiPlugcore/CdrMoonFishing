package id.menki.cdrmoonfishing.item;

import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.CatchObserver;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.UUID;

public final class CatchIdentityManager implements CatchObserver {
    private final NamespacedKey fishIdKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey caughtAtKey;
    private final NamespacedKey catchUidKey;

    public CatchIdentityManager(JavaPlugin plugin) {
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.weightKey = new NamespacedKey(plugin, "weight_kg");
        this.caughtAtKey = new NamespacedKey(plugin, "caught_at");
        this.catchUidKey = new NamespacedKey(plugin, "catch_uid");
    }

    @Override
    public void onCatch(Player player, FishDefinition fish, double weight) {
        int slot = findFreshCatch(player, fish.id(), weight);
        if (slot < 0) return;
        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return;

        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(catchUidKey, PersistentDataType.STRING)) {
            pdc.set(catchUidKey, PersistentDataType.STRING, UUID.randomUUID().toString());
            item.setItemMeta(meta);
            player.getInventory().setItem(slot, item);
        }
    }

    private int findFreshCatch(Player player, String fishId, double weight) {
        ItemStack[] storage = player.getInventory().getStorageContents();
        int selected = -1;
        long newest = Long.MIN_VALUE;
        long now = System.currentTimeMillis();

        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            if (item == null || !item.hasItemMeta()) continue;
            PersistentDataContainer pdc = item.getItemMeta().getPersistentDataContainer();
            String storedId = pdc.get(fishIdKey, PersistentDataType.STRING);
            Double storedWeight = pdc.get(weightKey, PersistentDataType.DOUBLE);
            Long caughtAt = pdc.get(caughtAtKey, PersistentDataType.LONG);
            if (storedId == null || !storedId.equalsIgnoreCase(fishId) || storedWeight == null) continue;
            if (Math.abs(storedWeight - weight) > 0.011) continue;
            if (caughtAt != null && Math.abs(now - caughtAt) > 10_000L) continue;

            long timestamp = caughtAt == null ? 0L : caughtAt;
            if (selected < 0 || timestamp > newest) {
                selected = slot;
                newest = timestamp;
            }
        }
        return selected;
    }
}
