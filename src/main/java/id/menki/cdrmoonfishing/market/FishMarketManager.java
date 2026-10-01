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
    public static final int SLOT_PRICE_LIST = 10;
    public static final int SLOT_FEATURED = 12;
    public static final int SLOT_HELD = 14;
    public static final int SLOT_SELL_HELD = 16;
    public static final int SLOT_SELL_ALL = 22;
    public static final int SLOT_SECURITY = 25;
    public static final int SLOT_CLOSE = 26;

    private static final int PRICE_PAGE_SIZE = 18;
    private static final int SLOT_PRICE_PREVIOUS = 27;
    private static final int SLOT_PRICE_LEGEND = 30;
    private static final int SLOT_PRICE_BACK = 31;
    private static final int SLOT_PRICE_NEXT = 35;

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
                Component.text("Pasar Ikan Bulan", NamedTextColor.DARK_AQUA));

        inventory.setItem(SLOT_PRICE_LIST, button(Material.WRITABLE_BOOK, "Daftar Harga Semua Ikan", NamedTextColor.AQUA,
                List.of(
                        Component.text(registry.definitions().size() + " spesies terdaftar", NamedTextColor.GRAY),
                        Component.text("Klik untuk membuka daftar harga per halaman.", NamedTextColor.GREEN),
                        Component.text("🔥 menandakan ikan dapat dimasak.", NamedTextColor.GOLD)
                )));
        inventory.setItem(SLOT_FEATURED, featuredCard());
        inventory.setItem(SLOT_HELD, heldCard(player));
        inventory.setItem(SLOT_SELL_HELD, button(Material.EMERALD, "Jual Ikan di Tangan", NamedTextColor.GREEN,
                List.of(Component.text("Jual ikan custom yang sedang kamu pegang.", NamedTextColor.GRAY))));
        inventory.setItem(SLOT_SELL_ALL, sellAllCard(player));
        inventory.setItem(SLOT_CLOSE, button(Material.BARRIER, "Tutup", NamedTextColor.RED, List.of()));
        inventory.setItem(SLOT_SECURITY, button(Material.SHIELD, "Keamanan Pasar", NamedTextColor.AQUA,
                List.of(Component.text("Anti-duplikat tangkapan unik: AKTIF", NamedTextColor.GREEN),
                        Component.text("ID tangkapan terjual: " + soldLedger.size(), NamedTextColor.DARK_GRAY))));

        if (!plugin.getConfig().getBoolean("economy.enabled", true)) {
            inventory.setItem(24, button(Material.REDSTONE_BLOCK, "Pasar Dinonaktifkan", NamedTextColor.RED,
                    List.of(Component.text("economy.enabled = false di config.yml", NamedTextColor.GRAY))));
        } else if (!economy.ensureReady()) {
            inventory.setItem(24, button(Material.REDSTONE_BLOCK, "Ekonomi Vault Offline", NamedTextColor.RED,
                    List.of(Component.text("Pasang/atur Vault + provider ekonomi.", NamedTextColor.GRAY))));
        }

        player.openInventory(inventory);
    }

    public void openPriceList(Player player, int requestedPage) {
        List<FishDefinition> fish = sortedFish();
        int pageCount = Math.max(1, (int) Math.ceil(fish.size() / (double) PRICE_PAGE_SIZE));
        int page = Math.max(0, Math.min(requestedPage, pageCount - 1));

        Inventory inventory = Bukkit.createInventory(new FishPriceListHolder(page), 36,
                Component.text("Daftar Harga Ikan • " + (page + 1) + "/" + pageCount, NamedTextColor.DARK_AQUA));

        int from = page * PRICE_PAGE_SIZE;
        int to = Math.min(fish.size(), from + PRICE_PAGE_SIZE);
        for (int index = from; index < to; index++) {
            inventory.setItem(index - from, speciesCard(fish.get(index)));
        }

        if (page > 0) {
            inventory.setItem(SLOT_PRICE_PREVIOUS, button(Material.ARROW, "Halaman Sebelumnya", NamedTextColor.YELLOW,
                    List.of(Component.text("Halaman " + page, NamedTextColor.GRAY))));
        }
        inventory.setItem(SLOT_PRICE_LEGEND, button(Material.CAMPFIRE, "Keterangan Memasak", NamedTextColor.GOLD,
                List.of(
                        Component.text("🔥 Bisa dimasak di Furnace/Smoker/Campfire", NamedTextColor.GREEN),
                        Component.text("✖ Tidak dapat dimasak", NamedTextColor.DARK_GRAY)
                )));
        inventory.setItem(SLOT_PRICE_BACK, button(Material.OAK_DOOR, "Kembali ke Pasar", NamedTextColor.AQUA, List.of()));
        if (page + 1 < pageCount) {
            inventory.setItem(SLOT_PRICE_NEXT, button(Material.ARROW, "Halaman Berikutnya", NamedTextColor.YELLOW,
                    List.of(Component.text("Halaman " + (page + 2), NamedTextColor.GRAY))));
        }

        player.openInventory(inventory);
    }

    public boolean isMarket(Inventory inventory) {
        if (inventory == null) return false;
        return inventory.getHolder() instanceof FishMarketHolder
                || inventory.getHolder() instanceof FishPriceListHolder;
    }

    public void handleClick(Player player, int rawSlot) {
        Inventory top = player.getOpenInventory().getTopInventory();
        if (top.getHolder() instanceof FishPriceListHolder holder) {
            handlePriceListClick(player, holder.page(), rawSlot);
            return;
        }

        switch (rawSlot) {
            case SLOT_PRICE_LIST -> openPriceList(player, 0);
            case SLOT_SELL_HELD -> { sellHeld(player); refreshNextTick(player); }
            case SLOT_SELL_ALL -> { sellAll(player); refreshNextTick(player); }
            case SLOT_CLOSE -> player.closeInventory();
            default -> { }
        }
    }

    private void handlePriceListClick(Player player, int page, int rawSlot) {
        int pageCount = Math.max(1, (int) Math.ceil(registry.definitions().size() / (double) PRICE_PAGE_SIZE));
        if (rawSlot == SLOT_PRICE_PREVIOUS && page > 0) {
            openPriceList(player, page - 1);
        } else if (rawSlot == SLOT_PRICE_NEXT && page + 1 < pageCount) {
            openPriceList(player, page + 1);
        } else if (rawSlot == SLOT_PRICE_BACK) {
            open(player);
        }
    }

    public void sendHeldQuote(Player player) {
        FishQuote quote = quote(player.getInventory().getItemInMainHand());
        if (quote == null) {
            player.sendMessage(Component.text("Pegang ikan CdrMoonFishing yang valid terlebih dahulu.", NamedTextColor.RED));
            return;
        }
        if (!isSellableIdentity(quote)) {
            player.sendMessage(Component.text("Identitas tangkapan ini tidak dapat dijual.", NamedTextColor.RED));
            return;
        }
        player.sendMessage(Component.text("Harga pasar: ", NamedTextColor.AQUA)
                .append(Component.text(quote.fish().displayName(), NamedTextColor.WHITE))
                .append(Component.text(String.format(Locale.US, " • %.2f kg • ", quote.weight()), NamedTextColor.GRAY))
                .append(Component.text(economy.format(quote.totalValue()), NamedTextColor.GREEN)));
        if (quote.featured()) player.sendMessage(Component.text("Bonus Tangkapan Unggulan aktif untuk spesies ini.", NamedTextColor.GOLD));
    }

    public void sendFeatured(Player player) {
        FishDefinition featured = featuredFish();
        if (featured == null) {
            player.sendMessage(Component.text("Tangkapan Unggulan sedang dinonaktifkan.", NamedTextColor.GRAY));
            return;
        }
        double multiplier = Math.max(1.0, plugin.getConfig().getDouble("economy.market.featured-multiplier", 1.35));
        player.sendMessage(Component.text("Tangkapan Unggulan Hari Ini: ", NamedTextColor.GOLD)
                .append(Component.text(featured.displayName(), NamedTextColor.YELLOW))
                .append(Component.text(String.format(Locale.US, " • nilai pasar x%.2f", multiplier), NamedTextColor.GRAY)));
    }

    public SaleResult sellHeld(Player player) {
        if (!marketReady(player)) return new SaleResult(false, 0, 0.0);

        ItemStack current = player.getInventory().getItemInMainHand();
        FishQuote quote = quote(current);
        if (quote == null) {
            player.sendMessage(Component.text("Item di tanganmu bukan ikan custom yang dapat dijual.", NamedTextColor.RED));
            return new SaleResult(false, 0, 0.0);
        }
        if (!validateSaleIdentity(player, quote)) return new SaleResult(false, 0, 0.0);

        String reservedUid = quote.catchUid();
        if (reservedUid != null && !soldLedger.markSold(reservedUid)) {
            player.sendMessage(Component.text("Penjualan diblokir: ID tangkapan gagal diamankan atau sudah pernah dijual.", NamedTextColor.RED));
            return new SaleResult(false, 0, 0.0);
        }

        ItemStack backup = current.clone();
        player.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
        DepositResult deposit = economy.deposit(player, quote.totalValue());
        if (!deposit.success()) {
            if (reservedUid != null) soldLedger.unmarkSold(reservedUid);
            restoreItem(player, backup);
            player.sendMessage(Component.text("Penjualan gagal: " + deposit.error(), NamedTextColor.RED));
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
            if (!isSellableIdentity(quote)) { rejected++; continue; }
            if (quote.catchUid() != null && !seenInBatch.add(quote.catchUid())) { rejected++; continue; }
            slots.add(slot);
            backups.add(item.clone());
            identities.add(quote.catchUid());
            total += quote.totalValue();
            count += quote.amount();
        }

        total = roundMoney(total);
        if (slots.isEmpty() || total <= 0.0) {
            player.sendMessage(Component.text("Tidak ada ikan custom yang memenuhi syarat jual di inventory.", NamedTextColor.RED));
            if (rejected > 0) player.sendMessage(Component.text(rejected + " stack ikan ditolak oleh keamanan pasar.", NamedTextColor.YELLOW));
            return new SaleResult(false, 0, 0.0);
        }

        List<String> reserved = new ArrayList<>();
        for (String uid : identities) {
            if (uid == null) continue;
            if (!soldLedger.markSold(uid)) {
                for (String rollback : reserved) soldLedger.unmarkSold(rollback);
                player.sendMessage(Component.text("Penjualan diblokir: ada ID tangkapan yang sudah dijual atau ledger gagal disimpan.", NamedTextColor.RED));
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
            player.sendMessage(Component.text("Penjualan gagal: " + deposit.error(), NamedTextColor.RED));
            return new SaleResult(false, 0, 0.0);
        }

        saleMessage(player, count, total);
        if (rejected > 0) player.sendMessage(Component.text(rejected + " stack ikan dilewati oleh keamanan pasar.", NamedTextColor.YELLOW));
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

    public int redeemedCatchCount() { return soldLedger.size(); }
    public void reloadSecurityLedger() { soldLedger.reload(); }

    private boolean validateSaleIdentity(Player player, FishQuote quote) {
        if (quote.catchUid() == null) {
            if (plugin.getConfig().getBoolean("security.market.require-catch-uid", false)) {
                player.sendMessage(Component.text("Ikan lama tanpa identitas tangkapan v1.0 tidak dapat dijual di server ini.", NamedTextColor.RED));
                return false;
            }
            return true;
        }
        if (quote.amount() != 1) {
            player.sendMessage(Component.text("Penjualan diblokir: tangkapan ber-ID tidak boleh ditumpuk.", NamedTextColor.RED));
            return false;
        }
        if (soldLedger.isSold(quote.catchUid())) {
            player.sendMessage(Component.text("Penjualan diblokir: identitas tangkapan ini sudah pernah dijual.", NamedTextColor.RED));
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
            player.sendMessage(Component.text("Pasar Ikan dinonaktifkan oleh konfigurasi server.", NamedTextColor.RED));
            return false;
        }
        if (!economy.ensureReady()) {
            player.sendMessage(Component.text("Provider ekonomi Vault tidak tersedia.", NamedTextColor.RED));
            return false;
        }
        return true;
    }

    private List<FishDefinition> sortedFish() {
        List<FishDefinition> fish = new ArrayList<>(registry.definitions().values());
        fish.sort(Comparator.comparingInt((FishDefinition value) -> value.rarity().ordinal())
                .thenComparing(FishDefinition::displayName, String.CASE_INSENSITIVE_ORDER));
        return fish;
    }

    private ItemStack speciesCard(FishDefinition fish) {
        boolean featured = isFeatured(fish);
        double oneKg = price(fish, 1.0, 1, featured);
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Harga dasar: " + economy.format(fish.basePricePerKg()) + " / kg", NamedTextColor.GRAY));
        lore.add(Component.text("Harga 1 kg saat ini: " + economy.format(oneKg), NamedTextColor.GREEN));
        lore.add(Component.text(String.format(Locale.US, "Berat: %.2f–%.2f kg", fish.minWeight(), fish.maxWeight()), NamedTextColor.GRAY));
        lore.add(Component.text("Kelangkaan: " + fish.rarity().displayName(), NamedTextColor.DARK_GRAY));
        if (fish.cookable()) {
            lore.add(Component.text("🔥 Bisa dimasak", NamedTextColor.GOLD));
        } else {
            lore.add(Component.text("✖ Tidak dapat dimasak", NamedTextColor.DARK_GRAY));
        }
        if (featured) lore.add(Component.text("★ TANGKAPAN UNGGULAN HARI INI", NamedTextColor.GOLD));
        return button(fish.material(), fish.displayName(), featured ? NamedTextColor.GOLD : NamedTextColor.AQUA, lore);
    }

    private ItemStack featuredCard() {
        FishDefinition featured = featuredFish();
        if (featured == null) {
            return button(Material.CLOCK, "Tangkapan Unggulan", NamedTextColor.GRAY,
                    List.of(Component.text("Fitur unggulan harian dinonaktifkan.", NamedTextColor.DARK_GRAY)));
        }
        double multiplier = Math.max(1.0, plugin.getConfig().getDouble("economy.market.featured-multiplier", 1.35));
        return button(Material.CLOCK, "Unggulan: " + featured.displayName(), NamedTextColor.GOLD,
                List.of(Component.text(String.format(Locale.US, "Bonus hari ini: x%.2f", multiplier), NamedTextColor.YELLOW),
                        Component.text("Berganti setiap hari.", NamedTextColor.GRAY)));
    }

    private ItemStack heldCard(Player player) {
        FishQuote quote = quote(player.getInventory().getItemInMainHand());
        if (quote == null) {
            return button(Material.BOOK, "Harga Ikan di Tangan", NamedTextColor.AQUA,
                    List.of(Component.text("Pegang ikan custom untuk melihat harganya.", NamedTextColor.GRAY)));
        }
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(String.format(Locale.US, "Berat: %.2f kg", quote.weight()), NamedTextColor.GRAY));
        lore.add(Component.text("Nilai: " + economy.format(quote.totalValue()), NamedTextColor.GREEN));
        lore.add(Component.text(quote.fish().cookable() ? "🔥 Bisa dimasak" : "✖ Tidak dapat dimasak",
                quote.fish().cookable() ? NamedTextColor.GOLD : NamedTextColor.DARK_GRAY));
        if (quote.featured()) lore.add(Component.text("★ Bonus Tangkapan Unggulan diterapkan", NamedTextColor.GOLD));
        if (quote.catchUid() != null) {
            lore.add(Component.text(soldLedger.isSold(quote.catchUid()) ? "Identitas: SUDAH DIJUAL" : "Identitas: VALID",
                    soldLedger.isSold(quote.catchUid()) ? NamedTextColor.RED : NamedTextColor.GREEN));
        } else lore.add(Component.text("Identitas: LEGACY", NamedTextColor.YELLOW));
        return button(quote.fish().material(), quote.fish().displayName(), NamedTextColor.AQUA, lore);
    }

    private ItemStack sellAllCard(Player player) {
        InventorySalePreview preview = previewAll(player);
        List<Component> lore = new ArrayList<>();
        if (preview.count() <= 0) {
            lore.add(Component.text("Tidak ada ikan valid untuk dijual.", NamedTextColor.GRAY));
        } else {
            lore.add(Component.text("Ikan valid: " + preview.count(), NamedTextColor.YELLOW));
            lore.add(Component.text("Perkiraan hasil: " + economy.format(preview.total()), NamedTextColor.GREEN));
            lore.add(Component.empty());
            lore.add(Component.text("Klik untuk menjual SEMUA ikan CdrMoonFishing", NamedTextColor.GOLD));
            lore.add(Component.text("yang valid di inventory sekaligus.", NamedTextColor.GRAY));
        }
        return button(Material.CHEST_MINECART, "JUAL SEMUA IKAN", NamedTextColor.GOLD, lore);
    }

    private InventorySalePreview previewAll(Player player) {
        Set<String> seen = new HashSet<>();
        int count = 0;
        double total = 0.0;
        for (ItemStack item : player.getInventory().getStorageContents()) {
            FishQuote quote = quote(item);
            if (quote == null || !isSellableIdentity(quote)) continue;
            if (quote.catchUid() != null && !seen.add(quote.catchUid())) continue;
            count += quote.amount();
            total += quote.totalValue();
        }
        return new InventorySalePreview(count, roundMoney(total));
    }

    private ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) meta.lore(lore.stream()
                .map(line -> line.decoration(TextDecoration.ITALIC, false))
                .toList());
        item.setItemMeta(meta);
        return item;
    }

    private void saleMessage(Player player, int amount, double total) {
        player.sendMessage(Component.text("Berhasil menjual " + amount + " ikan seharga ", NamedTextColor.GRAY)
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

    private double roundMoney(double value) { return Math.round(value * 100.0) / 100.0; }

    public record FishQuote(FishDefinition fish, double weight, int amount, double totalValue,
                            boolean featured, String catchUid) {}
    public record SaleResult(boolean success, int amount, double total) {}
    private record InventorySalePreview(int count, double total) {}
}
