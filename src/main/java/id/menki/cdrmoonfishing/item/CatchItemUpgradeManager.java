package id.menki.cdrmoonfishing.item;

import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.CatchObserver;
import net.kyori.adventure.text.Component;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

public final class CatchItemUpgradeManager implements CatchObserver {
    private final FishItemProviderManager providers;

    private final NamespacedKey fishIdKey;
    private final NamespacedKey rarityKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey regionKey;
    private final NamespacedKey depthKey;
    private final NamespacedKey caughtAtKey;
    private final NamespacedKey behaviorKey;
    private final NamespacedKey baitKey;
    private final NamespacedKey catchUidKey;
    private final NamespacedKey providerKey;

    public CatchItemUpgradeManager(JavaPlugin plugin, FishItemProviderManager providers) {
        this.providers = providers;
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.rarityKey = new NamespacedKey(plugin, "rarity");
        this.weightKey = new NamespacedKey(plugin, "weight_kg");
        this.regionKey = new NamespacedKey(plugin, "region");
        this.depthKey = new NamespacedKey(plugin, "depth");
        this.caughtAtKey = new NamespacedKey(plugin, "caught_at");
        this.behaviorKey = new NamespacedKey(plugin, "behavior");
        this.baitKey = new NamespacedKey(plugin, "bait_used");
        this.catchUidKey = new NamespacedKey(plugin, "catch_uid");
        this.providerKey = new NamespacedKey(plugin, "item_provider");
    }

    @Override
    public void onCatch(Player player, FishDefinition fish, double weight) {
        FishItemDefinition definition = fish.itemDefinition();
        if (definition == null || definition.provider() == FishItemProvider.VANILLA) return;

        int slot = findFreshCatch(player, fish.id(), weight);
        if (slot < 0) return;

        ItemStack original = player.getInventory().getItem(slot);
        if (original == null || !original.hasItemMeta()) return;

        FishItemProviderManager.ItemResult result = providers.createBaseItem(fish);
        if (result.fallback()) return;

        ItemStack upgraded = mergeCatchMetadata(original, result.item(), definition, result.providerUsed());
        player.getInventory().setItem(slot, upgraded);
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

    private ItemStack mergeCatchMetadata(ItemStack caught, ItemStack providerBase, FishItemDefinition definition, String providerUsed) {
        ItemStack upgraded = providerBase.clone();
        upgraded.setAmount(caught.getAmount());

        ItemMeta caughtMeta = caught.getItemMeta();
        ItemMeta providerMeta = upgraded.getItemMeta();

        if (!definition.preserveProviderName() || !providerMeta.hasDisplayName()) {
            providerMeta.displayName(caughtMeta.displayName());
        }

        List<Component> caughtLore = caughtMeta.lore();
        if (definition.preserveProviderLore()) {
            List<Component> merged = new ArrayList<>();
            List<Component> providerLore = providerMeta.lore();
            if (providerLore != null && !providerLore.isEmpty()) {
                merged.addAll(providerLore);
                if (caughtLore != null && !caughtLore.isEmpty()) merged.add(Component.empty());
            }
            if (caughtLore != null) merged.addAll(caughtLore);
            providerMeta.lore(merged.isEmpty() ? null : merged);
        } else {
            providerMeta.lore(caughtLore == null ? null : new ArrayList<>(caughtLore));
        }

        PersistentDataContainer from = caughtMeta.getPersistentDataContainer();
        PersistentDataContainer to = providerMeta.getPersistentDataContainer();
        copyString(from, to, fishIdKey);
        copyString(from, to, rarityKey);
        copyDouble(from, to, weightKey);
        copyString(from, to, regionKey);
        copyInteger(from, to, depthKey);
        copyLong(from, to, caughtAtKey);
        copyString(from, to, behaviorKey);
        copyString(from, to, baitKey);
        copyString(from, to, catchUidKey);
        to.set(providerKey, PersistentDataType.STRING, providerUsed);

        upgraded.setItemMeta(providerMeta);
        return upgraded;
    }

    private void copyString(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        String value = from.get(key, PersistentDataType.STRING);
        if (value != null) to.set(key, PersistentDataType.STRING, value);
    }

    private void copyDouble(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        Double value = from.get(key, PersistentDataType.DOUBLE);
        if (value != null) to.set(key, PersistentDataType.DOUBLE, value);
    }

    private void copyInteger(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        Integer value = from.get(key, PersistentDataType.INTEGER);
        if (value != null) to.set(key, PersistentDataType.INTEGER, value);
    }

    private void copyLong(PersistentDataContainer from, PersistentDataContainer to, NamespacedKey key) {
        Long value = from.get(key, PersistentDataType.LONG);
        if (value != null) to.set(key, PersistentDataType.LONG, value);
    }
}
