package id.menki.cdrmoonfishing.rod;

import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.model.RodTierDefinition;
import id.menki.cdrmoonfishing.registry.RodRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.ToDoubleFunction;

public final class RodManager {
    private static final int BAR_LENGTH = 12;

    private final JavaPlugin plugin;
    private final RodRegistry registry;
    private final NamespacedKey rodIdKey;
    private final NamespacedKey rodXpKey;
    private final NamespacedKey rodTierKey;
    private ToDoubleFunction<Player> collectionLuckProvider = player -> 0.0;

    public RodManager(JavaPlugin plugin, RodRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
        this.rodIdKey = new NamespacedKey(plugin, "rod_id");
        this.rodXpKey = new NamespacedKey(plugin, "rod_xp");
        this.rodTierKey = new NamespacedKey(plugin, "rod_tier");
    }

    public ItemStack createRod(String tierId) {
        RodTierDefinition tier = registry.get(tierId);
        if (tier == null) tier = registry.firstTier();
        if (tier == null) return new ItemStack(Material.FISHING_ROD);

        ItemStack rod = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = rod.getItemMeta();
        meta.getPersistentDataContainer().set(rodIdKey, PersistentDataType.STRING, UUID.randomUUID().toString());
        meta.getPersistentDataContainer().set(rodXpKey, PersistentDataType.INTEGER, tier.minXp());
        meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, tier.id());
        rod.setItemMeta(meta);
        refreshMeta(rod);
        return rod;
    }

    public boolean isProgressionRod(ItemStack item) {
        if (item == null || item.getType() != Material.FISHING_ROD || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(rodIdKey, PersistentDataType.STRING);
    }

    public RodTierDefinition tier(ItemStack item) {
        if (!isProgressionRod(item)) return null;
        int xp = xp(item);
        RodTierDefinition calculated = registry.tierForXp(xp);
        if (calculated != null) return calculated;

        String stored = item.getItemMeta().getPersistentDataContainer().get(rodTierKey, PersistentDataType.STRING);
        return registry.get(stored);
    }

    public int xp(ItemStack item) {
        if (!isProgressionRod(item)) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(rodXpKey, PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(0, value);
    }

    public double reelMultiplier(Player player) {
        RodTierDefinition tier = tier(player.getInventory().getItemInMainHand());
        return tier == null ? 1.0 : tier.reelMultiplier();
    }

    public double rarityMultiplier(Player player, FishDefinition fish) {
        RodTierDefinition tier = tier(player.getInventory().getItemInMainHand());
        double rodLuck = tier == null ? 0.0 : tier.rarityLuck();
        double collectionLuck = Math.max(0.0, collectionLuckProvider.applyAsDouble(player));
        double luck = rodLuck + collectionLuck;

        return switch (fish.rarity()) {
            case COMMON -> 1.0;
            case UNCOMMON -> 1.0 + (luck * 0.50);
            case RARE -> 1.0 + luck;
            case EPIC -> 1.0 + (luck * 1.50);
            case LEGENDARY -> 1.0 + (luck * 2.00);
        };
    }

    public void setCollectionLuckProvider(ToDoubleFunction<Player> provider) {
        this.collectionLuckProvider = provider == null ? player -> 0.0 : provider;
    }

    public void recordCatch(Player player, FishDefinition fish, double weight) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!isProgressionRod(rod)) return;

        RodTierDefinition tier = tier(rod);
        if (tier == null) return;

        int base = registry.baseXp(fish.rarity().name(), defaultBaseXp(fish.rarity()));
        double raw = (base + (weight * registry.weightXpMultiplier())) * tier.xpMultiplier();
        int gain = Math.max(1, (int) Math.round(raw));
        addXp(player, gain);
    }

    public boolean addXp(Player player, int amount) {
        if (amount <= 0) return false;
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!isProgressionRod(rod)) {
            player.sendMessage(Component.text("Hadiah XP joran tertunda: pegang joran progres CdrMoonFishing.", NamedTextColor.YELLOW));
            return false;
        }

        RodTierDefinition before = tier(rod);
        if (before == null) return false;

        int newXp = xp(rod) + amount;
        ItemMeta meta = rod.getItemMeta();
        meta.getPersistentDataContainer().set(rodXpKey, PersistentDataType.INTEGER, newXp);
        RodTierDefinition after = registry.tierForXp(newXp);
        if (after != null) meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, after.id());
        rod.setItemMeta(meta);
        refreshMeta(rod);

        player.sendActionBar(Component.text("XP Joran +" + amount + " • " + newXp + " XP", NamedTextColor.AQUA));
        if (after != null && !after.id().equals(before.id())) {
            player.sendTitle("§b§lJORAN NAIK TINGKAT!", "§f" + after.displayName(), 5, 40, 10);
            player.sendMessage(Component.text("Joran pancing naik menjadi ", NamedTextColor.GRAY)
                    .append(Component.text(after.displayName(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD)));
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.35f);
        }
        return true;
    }

    public void refreshMeta(ItemStack rod) {
        if (!isProgressionRod(rod)) return;
        RodTierDefinition tier = tier(rod);
        if (tier == null) return;

        int currentXp = xp(rod);
        RodTierDefinition next = registry.nextTier(tier);
        ItemMeta meta = rod.getItemMeta();
        meta.displayName(Component.text(tier.displayName(), NamedTextColor.AQUA).decorate(TextDecoration.BOLD));

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Joran Progres CdrMoonFishing", NamedTextColor.DARK_AQUA));
        lore.add(Component.empty());
        lore.add(Component.text("Tingkat: ", NamedTextColor.GRAY)
                .append(Component.text(tier.displayName(), NamedTextColor.AQUA)));
        lore.add(Component.text(String.format(Locale.US, "Kekuatan Tarik: %.2fx", tier.reelMultiplier()), NamedTextColor.GRAY));
        lore.add(Component.text(String.format(Locale.US, "Luck Kelangkaan: +%.0f%%", tier.rarityLuck() * 100.0), NamedTextColor.GRAY));
        lore.add(Component.text(String.format(Locale.US, "Perolehan XP Joran: %.2fx", tier.xpMultiplier()), NamedTextColor.GRAY));
        lore.add(Component.empty());

        if (next == null) {
            lore.add(Component.text("XP: " + currentXp + " • TINGKAT MAKS", NamedTextColor.GOLD));
            lore.add(Component.text("[" + "▰".repeat(BAR_LENGTH) + "]", NamedTextColor.GOLD));
        } else {
            int tierStart = tier.minXp();
            int target = next.minXp();
            int range = Math.max(1, target - tierStart);
            double progress = Math.max(0.0, Math.min(1.0, (currentXp - tierStart) / (double) range));
            int filled = Math.max(0, Math.min(BAR_LENGTH, (int) Math.round(progress * BAR_LENGTH)));
            String bar = "▰".repeat(filled) + "▱".repeat(BAR_LENGTH - filled);
            lore.add(Component.text("XP: " + currentXp + " / " + target, NamedTextColor.GRAY));
            lore.add(Component.text("[" + bar + "]", NamedTextColor.GREEN));
            lore.add(Component.text("Berikutnya: " + next.displayName(), NamedTextColor.DARK_GRAY));
        }

        meta.lore(lore);
        meta.getPersistentDataContainer().set(rodTierKey, PersistentDataType.STRING, tier.id());
        rod.setItemMeta(meta);
    }

    public RodRegistry registry() {
        return registry;
    }

    private int defaultBaseXp(FishRarity rarity) {
        return switch (rarity) {
            case COMMON -> 5;
            case UNCOMMON -> 8;
            case RARE -> 16;
            case EPIC -> 35;
            case LEGENDARY -> 100;
        };
    }
}
