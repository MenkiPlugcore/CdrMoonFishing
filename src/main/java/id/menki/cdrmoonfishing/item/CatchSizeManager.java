package id.menki.cdrmoonfishing.item;

import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishSizeClass;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.CatchObserver;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * Tags every fresh catch with a relative size class derived from the species'
 * configured weight range. The tag is already usable by future resource-pack
 * model variants even before those assets exist.
 */
public final class CatchSizeManager implements CatchObserver {
    private final NamespacedKey fishIdKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey caughtAtKey;
    private final NamespacedKey sizeClassKey;

    public CatchSizeManager(JavaPlugin plugin) {
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.weightKey = new NamespacedKey(plugin, "weight_kg");
        this.caughtAtKey = new NamespacedKey(plugin, "caught_at");
        this.sizeClassKey = new NamespacedKey(plugin, "fish_size");
    }

    @Override
    public void onCatch(Player player, FishDefinition fish, double weight) {
        int slot = findFreshCatch(player, fish.id(), weight);
        if (slot < 0) return;

        ItemStack item = player.getInventory().getItem(slot);
        if (item == null || !item.hasItemMeta()) return;

        FishSizeClass sizeClass = FishSizeClass.fromWeight(fish, weight);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(sizeClassKey, PersistentDataType.STRING, sizeClass.id());

        List<Component> lore = meta.lore() == null ? new ArrayList<>() : new ArrayList<>(meta.lore());
        int insertAt = lore.isEmpty() ? 0 : Math.max(0, lore.size() - 1);
        lore.add(insertAt, Component.text("Ukuran: ", NamedTextColor.DARK_GRAY)
                .append(Component.text(sizeClass.displayName(), sizeClass.color())));
        meta.lore(lore);
        item.setItemMeta(meta);
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
