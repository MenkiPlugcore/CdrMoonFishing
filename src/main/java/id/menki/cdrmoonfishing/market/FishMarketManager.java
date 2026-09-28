package id.menki.cdrmoonfishing.market;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.economy.VaultEconomyHook.DepositResult;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.registry.FishRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FishMarketManager {
    public static final int SLOT_FEATURED = 11;
    public static final int SLOT_HELD = 13;
    public static final int SLOT_SELL_HELD = 15;
    public static final int SLOT_SELL_ALL = 17;
    public static final int SLOT_CLOSE = 22;

    private final CdrMoonFishing plugin;
    private final FishRegistry registry;
    private final VaultEconomyHook economy;
    private final NamespacedKey fishIdKey;
    private final NamespacedKey weightKey;
    private final NamespacedKey catchUidKey;
    private final SoldCatchLedger soldLedger;

    public FishMarketManager(CdrMoonFishing plugin, FishRegistry registry, VaultEconomyHook economy) {
        this.plugin = plugin;
        this.registry = registry;
        this.economy = economy;
        this.fishIdKey = new NamespacedKey(plugin, "fish_id");
        this.weightKey = new NamespacedKey(plugin, "weight_kg");
        this.catchUidKey = new NamespacedKey(plugin, "catch_uid");
        this.soldLedger = new SoldCatchLedger(plugin);
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(new FishMarketHolder(), 27,
                Component.text("Moon Fish Market", NamedTextColor.DARK_AQUA));

        List<FishDefinition> fish = new ArrayList<>(registry.definitions().values());
        fish.sort(Comparator.comparingInt((FishDefinition value) -> value.rarity().ordinal())
                .thenComparing(FishDefinition::displayName, String.CASE_INSENSITIVE_ORDER));
        for (int slot = 0; slot < Math.min(9, fish.size()); slot++) {
            inventory.setItem(slot, speciesCard(fish.get(slot)));
        }

        inventory.setItem(SLOT_FEATURED, featuredCard());
        inventory.setItem(SLOT_HELD, heldCard(player));
        inventory.setItem(SLOT_SELL_HELD, button(Material.EMERALD, "Sell Held Fish", NamedTextColor.GREEN,
                List.of(Component.text("Sell the custom fish in your main hand.", NamedTextColor.GRAY))));
        inventory.setItem(SLOT_SELL_ALL, button(Material.CHEST, "Sell All Fish", NamedTextColor.GOLD,
                List.of(Component.text("Sell all valid CdrMoonFishing fish in storage slots.", NamedTextColor.GRAY))));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "Close", NamedTextColor.RED, List.of()));
        inventory.setItem(25, button(Material.SHIELD, "Market Security", NamedTextColor.AQUA,
                List.of(Component.text("Unique catch anti-duplicate ledger: ACTIVE", NamedTextColor.GREEN),
                        Component.text("Redeemed catch IDs: " + soldLedger.size(), NamedTextColor.DARK_GRAY))));

        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            inventory.setItem(26, button(Material.REDSTONE_BLOCK, "Market Disabled", NamedTextColor.RED,
                    List.of(Component.text("economy.enabled is false in config.yml", NamedTextColor.GRAY))));
        } else if (!economy.ensureReady()) {
            inventory.setItem(26, button(Material.REDSTONE_BLOCK, "Vault Economy Offline", NamedTextColor.RED,
                    List.of(Component.text("Install/configure Vault + an economy provider.", NamedTextColor.GRAY))));
        }

        player.openInventory(inventory);
    }

    public boolean isMarket(Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof FishMarketHolder;
    }

    public void handleClick(Player player, int rawSlot) {
        switch (rawSlot) {
            case SLOT_SELL_HELD -> {
                sellHeld(player);
                refreshNextTick(player);
            }
            case SLOT_SELL_ALL -> {
                sellAll(player);
                refreshNextTick(player);
            }
            case SLOT_CLOSE -> player.closeInventory();
            default -> { }
        }
    }

    public void sendHeldQuote(Player player) {
        FishQuote quote = quote(player.getInventory().getItemInMainHand());
        if (quote == null) {
            player.sendMessage(Component.text("Hold a valid CdrMoonFishing fish first.", NamedTextColor.RED));
            return;
        }
        if (!isSellableIdentity(quote)) {
            player.sendMessage(Component.text("This catch identity is not eligible for sale.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Market quote: ", NamedTextColor.AQUA)
                .append(Component.text(quote.fish().displayName(), NamedTextColor.WHITE))
                .append(Component.text(String.format(Locale.US, " • %.2f kg • ", quote.weight()), NamedTextColor.GRAY))
                .append(Component.text(economy.format(quote.totalValue()), NamedTextColor.GREEN)));
        if (quote.featured()) {
            player.sendMessage(Component.text("Featured Catch bonus is active for this species.", NamedTextColor.GOLD));
        }
    }

    public void sendFeatured(Player player) {
        FishDefinition featured = featuredFish();
        if (featured == null) {
            player.sendMessage(Component.text("Featured Catch is currently disabled.", NamedTextColor.GRAY));
            return;
        }
        double multiplier = Math.max(1.0, plugin.getConfig().getDouble("economy.market.featured-multiplier", 1.35));
        player.sendMessage(Component.text("Today's Featured Catch: ", NamedTextColor.GOLD)
                .append(Component.text(featured.displayName(), NamedTextColor.YELLOW))
                .append(Component.text(String.format(Locale.US, " • x%.2f market value", multiplier), NamedTextColor.GRAY)));
    }

    public SaleResult sellHeld(Player player) {
        if (!marketReady(player)) return new SaleResult(false, 0, 0.0);

        ItemStack current = player.getInventory().getItemInMainHand();
        FishQuote quote = quote(current);
        if (quote == null) {
            player.sendMessage(Component.text("The item in your hand is not a sellable custom fish.", NamedTextColor.RED));
            return new SaleResult(false, 0, 0.0);
        }
        if (!validateSaleIdentity(player, quote)) return new SaleResult(false, 0, 0.0);

        String reservedUid = quote.catchUid();
        if (reservedUid != null && !soldLedger.markSold(reservedUid)) {
            player.sendMessage(Component.text("Sale blocked: catch identity could not be reserved or was already redeemed.", NamedTextColor.RED));
            return new SaleResult(false, 0, 0.0);
        }

        ItemStack backup = current.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        DepositResult deposit = economy.deposit(player, quote.totalValue());
        if (!deposit.success()) {
            if (reservedUid != null) soldLedger.unmarkSold(reservedUid);
            restoreItem(player, backup);
            player.sendMessage(Component.text("Sale failed: " + deposit.error(), NamedTextColor.RED));
            return new SaleResult(false, 0, 0.0);
        }

        saleMessage(player, quote.amount(), quote.totalValue());
        return new SaleResult(true, quote.amount(), quote.totalValue());
    }

    public SaleResult sellAll(Player player) {
        if (!marketReady(player)) return new SaleResult(false, 0, 0.0);

        ItemStack[] storage = player.getInventory().getStorageContents();
        List<Integer> slots = new ArrayList<>();
        List<ItemStack> backups = new ArrayList<>();
        List<String> identities = new ArrayList<>();
        Set<String> seenInBatch = new HashSet<>();
        double total = 0.0;
        int count = 0;
        int rejected = 0;

        for (int slot = 0; slot < storage.length; slot++) {
            ItemStack item = storage[slot];
            FishQuote quote = quote(item);
            if (quote == null) continue;
            if (!isSellableIdentity(quote)) {
                rejected++;
                continue;
            }
            if (quote.catchUid() != null && !seenInBatch.add(quote.catchUid())) {
                rejected++;
                continue;
            }
            slots.add(slot);
            backups.add(item.clone());
            identities.add(quote.catchUid());
            total += quote.totalValue();
            count += quote.amount();
        }

        total = roundMoney(total);
        if (slots.isEmpty() || total <= 0.0) {
            player.sendMessage(Component.text("No eligible custom fish found in your inventory.", NamedTextColor.RED));
            if (rejected > 0) player.sendMessage(Component.text(rejected + " fish stack(s) rejected by market security.", NamedTextColor.YELLOW));
            return new SaleResult(false, 0, 0.0);
        }

        List<String> reserved = new ArrayList<>();
        for (String uid : identities) {
            if (uid == null) continue;
            if (!soldLedger.markSold(uid)) {
                for (String rollback : reserved) soldLedger.unmarkSold(rollback);
                player.sendMessage(Component.text("Sale blocked: one catch identity was already redeemed or ledger persistence failed.", NamedTextColor.RED));
                return new SaleResult(false, 0, 0.0);
            }
            reserved.add(uid);
        }

        for (int slot : slots) player.getInventory().setItem(slot, null);
        DepositResult deposit = economy.deposit(player, total);
        if (!deposit.success()) {
            for (String uid : reserved) soldLedger.unmarkSold(uid);
            for (int i = 0; i < slots.size(); i++) {
                int slot = slots.get(i);
                ItemStack backup = backups.get(i);
                if (player.getInventory().getItem(slot) == null) player.getInventory().setItem(slot, backup);
                else restoreItem(player, backup);
            }
            player.sendMessage(Component.text("Sale failed: " + deposit.error(), NamedTextColor.RED));
            return new SaleResult(false, 0, 0.0);
        }

        saleMessage(player, count, total);
        if (rejected > 0) {
            player.sendMessage(Component.text(rejected + " fish stack(s) were skipped by market security.", NamedTextColor.YELLOW));
        }
        return new SaleResult(true, count, total);
    }

    public FishQuote quote(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return null;
        ItemMeta meta = item.getItemMeta();
        String fishId = meta.getPersistentDataContainer().get(fishIdKey, PersistentDataType.STRING);
        Double weight = meta.getPersistentDataContainer().get(weightKey, PersistentDataType.DOUBLE);
        String catchUid = meta.getPersistentDataContainer().get(catchUidKey, PersistentDataType.STRING);
        if (fishId == null || weight == null || weight <= 0.0) return null;

        FishDefinition fish = registry.get(fishId);
        if (fish == null || fish.basePricePerKg() <= 0.0) return null;

        int amount = Math.max(1, item.getAmount());
        boolean featured = isFeatured(fish);
        double total = price(fish, weight, amount, featured);
        if (total <= 0.0) return null;
        return new FishQuote(fish, weight, amount, total, featured, catchUid);
    }

    public FishDefinition featuredFish() {
        if (!plugin.getConfig().getBoolean("economy.market.featured-enabled", true)) return null;
        List<FishDefinition> fish = registry.definitions().values().stream()
                .filter(value -> value.basePricePerKg() > 0.0)
                .sorted(Comparator.comparing(FishDefinition::id))
                .toList();
        if (fish.isEmpty()) return null;

        ZoneId zone;
        try {
            zone = ZoneId.of(plugin.getConfig().getString("economy.market.timezone", "Asia/Jakarta"));
        } catch (Exception ignored) {
            zone = ZoneId.systemDefault();
        }
        long epochDay = LocalDate.now(zone).toEpochDay();
        int index = Math.floorMod(epochDay, fish.size());
        return fish.get(index);
    }

    public int redeemedCatchCount() {
        return soldLedger.size();
    }

    public void reloadSecurityLedger() {
        soldLedger.reload();
    }

    private boolean validateSaleIdentity(Player player, FishQuote quote) {
        if (quote.catchUid() == null) {
            if (plugin.getConfig().getBoolean("security.market.require-catch-uid", false)) {
                player.sendMessage(Component.text("Legacy fish without a v1.0 catch identity cannot be sold on this server.", NamedTextColor.RED));
                return false;
            }
            return true;
        }
        if (quote.amount() != 1) {
            player.sendMessage(Component.text("Sale blocked: identified catches cannot be stacked.", NamedTextColor.RED));
            return false;
        }
        if (soldLedger.isSold(quote.catchUid())) {
            player.sendMessage(Component.text("Sale blocked: this catch identity has already been redeemed.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private boolean isSellableIdentity(FishQuote quote) {
        if (quote.catchUid() == null) return !plugin.getConfig().getBoolean("security.market.require-catch-uid", false);
        return quote.amount() == 1 && !soldLedger.isSold(quote.catchUid());
    }

    private double price(FishDefinition fish, double weight, int amount, boolean featured) {
        double global = Math.max(0.0, plugin.getConfig().getDouble("economy.global-multiplier", 1.0));
        double rarity = Math.max(0.0, plugin.getConfig().getDouble(
                "economy.rarity-multipliers." + fish.rarity().name(), defaultRarityMultiplier(fish)));
        double featuredMultiplier = featured
                ? Math.max(1.0, plugin.getConfig().getDouble("economy.market.featured-multiplier", 1.35))
                : 1.0;
        double raw = fish.basePricePerKg() * weight * rarity * global * featuredMultiplier * amount;
        double minimum = Math.max(0.0, plugin.getConfig().getDouble("economy.minimum-price", 1.0)) * amount;
        return roundMoney(Math.max(minimum, raw));
    }

    private double defaultRarityMultiplier(FishDefinition fish) {
        return switch (fish.rarity()) {
            case COMMON -> 1.0;
            case UNCOMMON -> 1.10;
            case RARE -> 1.30;
            case EPIC -> 1.65;
            case LEGENDARY -> 2.25;
        };
    }

    private boolean isFeatured(FishDefinition fish) {
        FishDefinition featured = featuredFish();
        return featured != null && featured.id().equalsIgnoreCase(fish.id());
    }

    private boolean marketReady(Player player) {
        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            player.sendMessage(Component.text("Fish Market is disabled by server configuration.", NamedTextColor.RED));
            return false;
        }
        if (!economy.ensureReady()) {
            player.sendMessage(Component.text("Vault economy provider is not available.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private ItemStack speciesCard(FishDefinition fish) {
        boolean featured = isFeatured(fish);
        double oneKg = price(fish, 1.0, 1, featured);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Base: " + economy.format(fish.basePricePerKg()) + " / kg", NamedTextColor.GRAY));
        lore.add(Component.text("Current 1 kg value: " + economy.format(oneKg), NamedTextColor.GREEN));
        lore.add(Component.text("Rarity: " + fish.rarity().name(), NamedTextColor.DARK_GRAY));
        if (featured) lore.add(Component.text("★ FEATURED CATCH TODAY", NamedTextColor.GOLD));
        return button(fish.material(), fish.displayName(), featured ? NamedTextColor.GOLD : NamedTextColor.AQUA, lore);
    }

    private ItemStack featuredCard() {
        FishDefinition featured = featuredFish();
        if (featured == null) {
            return button(Material.CLOCK, "Featured Catch", NamedTextColor.GRAY,
                    List.of(Component.text("Daily feature is disabled.", NamedTextColor.DARK_GRAY)));
        }
        double multiplier = Math.max(1.0, plugin.getConfig().getDouble("economy.market.featured-multiplier", 1.35));
        return button(Material.CLOCK, "Featured: " + featured.displayName(), NamedTextColor.GOLD,
                List.of(Component.text(String.format(Locale.US, "Today's bonus: x%.2f", multiplier), NamedTextColor.YELLOW),
                        Component.text("Rotates daily.", NamedTextColor.GRAY)));
    }

    private ItemStack heldCard(Player player) {
        FishQuote quote = quote(player.getInventory().getItemInMainHand());
        if (quote == null) {
            return button(Material.BOOK, "Held Fish Quote", NamedTextColor.AQUA,
                    List.of(Component.text("Hold a custom fish to see its price.", NamedTextColor.GRAY)));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format(Locale.US, "Weight: %.2f kg", quote.weight()), NamedTextColor.GRAY));
        lore.add(Component.text("Value: " + economy.format(quote.totalValue()), NamedTextColor.GREEN));
        if (quote.featured()) lore.add(Component.text("★ Featured Catch bonus applied", NamedTextColor.GOLD));
        if (quote.catchUid() != null) {
            lore.add(Component.text(soldLedger.isSold(quote.catchUid()) ? "Identity: REDEEMED" : "Identity: VALID",
                    soldLedger.isSold(quote.catchUid()) ? NamedTextColor.RED : NamedTextColor.GREEN));
        } else {
            lore.add(Component.text("Identity: LEGACY", NamedTextColor.YELLOW));
        }
        return button(quote.fish().material(), quote.fish().displayName(), NamedTextColor.AQUA, lore);
    }

    private ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore);
        item.setItemMeta(meta);
        return item;
    }

    private void saleMessage(Player player, int amount, double total) {
        player.sendMessage(Component.text("Sold " + amount + " fish for ", NamedTextColor.GRAY)
                .append(Component.text(economy.format(total), NamedTextColor.GREEN)));
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f);
    }

    private void restoreItem(Player player, ItemStack item) {
        var leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
    }

    private void refreshNextTick(Player player) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player);
        });
    }

    private double roundMoney(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    public record FishQuote(FishDefinition fish, double weight, int amount, double totalValue,
                            boolean featured, String catchUid) {}
    public record SaleResult(boolean success, int amount, double total) {}
}
