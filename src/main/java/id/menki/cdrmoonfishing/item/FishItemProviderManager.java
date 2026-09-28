package id.menki.cdrmoonfishing.item;

import id.menki.cdrmoonfishing.model.FishDefinition;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class FishItemProviderManager {
    private final JavaPlugin plugin;
    private final Set<String> warned = new HashSet<>();

    public FishItemProviderManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public ItemResult createBaseItem(FishDefinition fish) {
        FishItemDefinition definition = fish.itemDefinition() == null ? FishItemDefinition.vanilla() : fish.itemDefinition();
        return switch (definition.provider()) {
            case VANILLA -> vanilla(fish, false);
            case ITEMSADDER -> itemsAdder(fish, definition, true);
            case MMOITEMS -> mmoItems(fish, definition, true);
            case AUTO -> auto(fish, definition);
        };
    }

    private ItemResult auto(FishDefinition fish, FishItemDefinition definition) {
        if (definition.itemsAdderId() != null && !definition.itemsAdderId().isBlank()) {
            ItemResult result = itemsAdder(fish, definition, false);
            if (!result.fallback()) return result;
        }
        if (definition.mmoItemsType() != null && !definition.mmoItemsType().isBlank()
                && definition.mmoItemsId() != null && !definition.mmoItemsId().isBlank()) {
            ItemResult result = mmoItems(fish, definition, false);
            if (!result.fallback()) return result;
        }
        warnOnce(fish.id() + ":auto", "No configured custom item provider could build fish '" + fish.id() + "'; using vanilla fallback.");
        return vanilla(fish, true);
    }

    private ItemResult itemsAdder(FishDefinition fish, FishItemDefinition definition, boolean warn) {
        String id = definition.itemsAdderId();
        if (id == null || id.isBlank()) {
            if (warn) warnOnce(fish.id() + ":ia-id", "Fish '" + fish.id() + "' uses ITEMSADDER but itemsadder-id is empty; using vanilla fallback.");
            return vanilla(fish, true);
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder")) {
            if (warn) warnOnce(fish.id() + ":ia-offline", "ItemsAdder is not enabled for fish '" + fish.id() + "'; using vanilla fallback.");
            return vanilla(fish, true);
        }

        try {
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Method getInstance = customStackClass.getMethod("getInstance", String.class);
            Object customStack = getInstance.invoke(null, id);
            if (customStack == null) {
                if (warn) warnOnce(fish.id() + ":ia-missing", "ItemsAdder item '" + id + "' was not found for fish '" + fish.id() + "'; using vanilla fallback.");
                return vanilla(fish, true);
            }
            Method getItemStack = customStackClass.getMethod("getItemStack");
            Object raw = getItemStack.invoke(customStack);
            if (raw instanceof ItemStack item) {
                ItemStack clone = item.clone();
                clone.setAmount(1);
                return new ItemResult(clone, "ITEMSADDER", false);
            }
        } catch (ReflectiveOperationException | LinkageError ex) {
            if (warn) warnOnce(fish.id() + ":ia-error", "ItemsAdder API error for fish '" + fish.id() + "': " + ex.getMessage());
        }
        return vanilla(fish, true);
    }

    private ItemResult mmoItems(FishDefinition fish, FishItemDefinition definition, boolean warn) {
        String typeId = definition.mmoItemsType();
        String itemId = definition.mmoItemsId();
        if (typeId == null || typeId.isBlank() || itemId == null || itemId.isBlank()) {
            if (warn) warnOnce(fish.id() + ":mi-id", "Fish '" + fish.id() + "' uses MMOITEMS but type/id is incomplete; using vanilla fallback.");
            return vanilla(fish, true);
        }
        if (!plugin.getServer().getPluginManager().isPluginEnabled("MMOItems")) {
            if (warn) warnOnce(fish.id() + ":mi-offline", "MMOItems is not enabled for fish '" + fish.id() + "'; using vanilla fallback.");
            return vanilla(fish, true);
        }

        try {
            Class<?> mmoItemsClass = Class.forName("net.Indyuce.mmoitems.MMOItems");
            Field pluginField = mmoItemsClass.getField("plugin");
            Object instance = pluginField.get(null);
            if (instance == null) return vanilla(fish, true);

            Method getTypes = mmoItemsClass.getMethod("getTypes");
            Object typeManager = getTypes.invoke(instance);
            Method getType = typeManager.getClass().getMethod("get", String.class);
            Object type = getType.invoke(typeManager, typeId.toUpperCase(Locale.ROOT));
            if (type == null) {
                if (warn) warnOnce(fish.id() + ":mi-type", "MMOItems type '" + typeId + "' was not found for fish '" + fish.id() + "'.");
                return vanilla(fish, true);
            }

            Method itemMethod = null;
            for (Method method : mmoItemsClass.getMethods()) {
                if (!method.getName().equals("getItem") || method.getParameterCount() != 2) continue;
                Class<?>[] parameters = method.getParameterTypes();
                if (parameters[1] == String.class && parameters[0].isAssignableFrom(type.getClass())) {
                    itemMethod = method;
                    break;
                }
            }
            if (itemMethod == null) {
                if (warn) warnOnce(fish.id() + ":mi-method", "Compatible MMOItems#getItem(Type,String) API was not found; using vanilla fallback.");
                return vanilla(fish, true);
            }

            Object target = Modifier.isStatic(itemMethod.getModifiers()) ? null : instance;
            Object raw = itemMethod.invoke(target, type, itemId.toUpperCase(Locale.ROOT));
            if (raw instanceof ItemStack item) {
                ItemStack clone = item.clone();
                clone.setAmount(1);
                return new ItemResult(clone, "MMOITEMS", false);
            }
            if (warn) warnOnce(fish.id() + ":mi-missing", "MMOItems item '" + typeId + ":" + itemId + "' was not found; using vanilla fallback.");
        } catch (ReflectiveOperationException | LinkageError ex) {
            if (warn) warnOnce(fish.id() + ":mi-error", "MMOItems API error for fish '" + fish.id() + "': " + ex.getMessage());
        }
        return vanilla(fish, true);
    }

    private ItemResult vanilla(FishDefinition fish, boolean fallback) {
        return new ItemResult(new ItemStack(fish.material()), "VANILLA", fallback);
    }

    public String readiness(FishDefinition fish) {
        FishItemDefinition definition = fish.itemDefinition() == null ? FishItemDefinition.vanilla() : fish.itemDefinition();
        return switch (definition.provider()) {
            case VANILLA -> "VANILLA";
            case ITEMSADDER -> plugin.getServer().getPluginManager().isPluginEnabled("ItemsAdder") ? "ITEMSADDER" : "ITEMSADDER→VANILLA";
            case MMOITEMS -> plugin.getServer().getPluginManager().isPluginEnabled("MMOItems") ? "MMOITEMS" : "MMOITEMS→VANILLA";
            case AUTO -> "AUTO";
        };
    }

    public void clearWarnings() {
        warned.clear();
    }

    private void warnOnce(String key, String message) {
        if (warned.add(key)) plugin.getLogger().warning(message);
    }

    public record ItemResult(ItemStack item, String providerUsed, boolean fallback) {}
}
