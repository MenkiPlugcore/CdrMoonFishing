package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishSizeClass;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockCookEvent;
import org.bukkit.inventory.CampfireRecipe;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmokingRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Turns CdrMoonFishing catches into edible cooked fish while preserving catch
 * identity/weight metadata. Vanilla COD/SALMON recipes are reused. Extra
 * TROPICAL_FISH and PUFFERFISH recipes are supplied because Minecraft does not
 * normally allow those materials to be cooked.
 */
public final class FishCookingListener implements Listener {
    private final JavaPlugin plugin;
    private final FishRegistry registry;
    private final NamespacedKey fishIdKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey sizeClassKey;
    private final NamespacedKey cookedKey;

    public FishCookingListener(JavaPlugin plugin, FishRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.weightKey = new NamespacedKey(plugin, "weight_kg");
        this.sizeClassKey = new NamespacedKey(plugin, "fish_size");
        this.cookedKey = new NamespacedKey(plugin, "cooked_fish");
        registerFallbackRecipes();
    }

    @EventHandler(ignoreCancelled = true)
    public void onCook(BlockCookEvent event) {
        ItemStack source = event.getSource();
        if (source == null || !source.hasItemMeta()) return;

        ItemMeta sourceMeta = source.getItemMeta();
        PersistentDataContainer sourcePdc = sourceMeta.getPersistentDataContainer();
        String fishId = sourcePdc.get(fishIdKey, PersistentDataType.STRING);
        if (fishId == null) return;
        if (sourcePdc.has(cookedKey, PersistentDataType.BYTE)) return;

        FishDefinition fish = registry.get(fishId);
        Material cookedMaterial = fish != null && fish.material() == Material.SALMON
                ? Material.COOKED_SALMON
                : Material.COOKED_COD;

        ItemStack cooked = new ItemStack(cookedMaterial, 1);
        ItemMeta meta = cooked.getItemMeta();
        sourcePdc.copyTo(meta.getPersistentDataContainer(), true);
        meta.getPersistentDataContainer().set(cookedKey, PersistentDataType.BYTE, (byte) 1);

        String fishName = fish == null ? fishId : fish.displayName();
        meta.displayName(Component.text("Ikan Goreng ", NamedTextColor.GOLD)
                .append(Component.text(fishName, NamedTextColor.YELLOW)));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Masakan CdrMoonFishing", NamedTextColor.DARK_GRAY));

        Double weight = sourcePdc.get(weightKey, PersistentDataType.DOUBLE);
        if (weight != null) {
            lore.add(Component.text(String.format(Locale.US, "Berat: %.2f kg", weight), NamedTextColor.GRAY));
        }

        String rawSize = sourcePdc.get(sizeClassKey, PersistentDataType.STRING);
        if (rawSize != null) {
            FishSizeClass sizeClass = FishSizeClass.parse(rawSize);
            lore.add(Component.text("Ukuran: ", NamedTextColor.DARK_GRAY)
                    .append(Component.text(sizeClass.displayName(), sizeClass.color())));
        }

        lore.add(Component.text("Siap dimakan", NamedTextColor.GREEN));
        meta.lore(lore);
        cooked.setItemMeta(meta);
        event.setResult(cooked);
    }

    private void registerFallbackRecipes() {
        register(Material.TROPICAL_FISH, "tropical_fish");
        register(Material.PUFFERFISH, "pufferfish");
    }

    private void register(Material source, String id) {
        ItemStack placeholder = new ItemStack(Material.COOKED_COD);

        NamespacedKey furnaceKey = new NamespacedKey(plugin, "cook_" + id + "_furnace");
        NamespacedKey smokerKey = new NamespacedKey(plugin, "cook_" + id + "_smoker");
        NamespacedKey campfireKey = new NamespacedKey(plugin, "cook_" + id + "_campfire");

        Bukkit.addRecipe(new FurnaceRecipe(furnaceKey, placeholder, source, 0.35f, 200));
        Bukkit.addRecipe(new SmokingRecipe(smokerKey, placeholder, source, 0.35f, 100));
        Bukkit.addRecipe(new CampfireRecipe(campfireKey, placeholder, source, 0.35f, 600));
    }
}
