package id.menki.cdrmoonfishing.supply;

import id.menki.cdrmoonfishing.economy.VaultEconomyHook;
import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.RodTierDefinition;
import id.menki.cdrmoonfishing.registry.BaitRegistry;
import id.menki.cdrmoonfishing.rod.RodManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class SupplyShopManager {
    private static final int STARTER_SLOT = 11;
    private static final int UPGRADE_SLOT = 13;
    private static final int BALANCE_SLOT = 15;
    private static final int[] BAIT_SLOTS = {18, 19, 20, 21, 22};

    private final JavaPlugin plugin;
    private final RodManager rodManager;
    private final BaitRegistry baitRegistry;
    private final VaultEconomyHook economy;
    private final File file;
    private YamlConfiguration config;
    private final Map<Integer, String> baitBySlot = new LinkedHashMap<>();

    public SupplyShopManager(JavaPlugin plugin, RodManager rodManager, BaitRegistry baitRegistry, VaultEconomyHook economy) {
        this.plugin = plugin;
        this.rodManager = rodManager;
        this.baitRegistry = baitRegistry;
        this.economy = economy;
        this.file = new File(plugin.getDataFolder(), "supply.yml");
        reload();
    }

    public void reload() {
        this.config = YamlConfiguration.loadConfiguration(file);
        rebuildBaitSlots();
    }

    public void open(Player player) {
        String title = config.getString("gui.title", "Fishing Supply");
        Inventory inventory = Bukkit.createInventory(new SupplyHolder(), 27, Component.text(title, NamedTextColor.DARK_AQUA));
        decorate(inventory);
        inventory.setItem(STARTER_SLOT, starterButton(player));
        inventory.setItem(UPGRADE_SLOT, upgradeButton(player));
        inventory.setItem(BALANCE_SLOT, balanceButton(player));

        for (Map.Entry<Integer, String> entry : baitBySlot.entrySet()) {
            BaitDefinition bait = baitRegistry.get(entry.getValue());
            if (bait != null) inventory.setItem(entry.getKey(), baitButton(bait));
        }
        player.openInventory(inventory);
    }

    public void handleClick(Player player, int rawSlot) {
        if (rawSlot == STARTER_SLOT) {
            claimStarter(player);
            reopen(player);
            return;
        }
        if (rawSlot == UPGRADE_SLOT) {
            upgradeHeldRod(player);
            reopen(player);
            return;
        }
        String baitId = baitBySlot.get(rawSlot);
        if (baitId != null) {
            buyBait(player, baitId);
            reopen(player);
        }
    }

    private void claimStarter(Player player) {
        RodTierDefinition starter = starterTier();
        if (starter == null) {
            player.sendMessage(Component.text("Tier starter tidak tersedia.", NamedTextColor.RED));
            return;
        }
        if (hasAnyProgressionRod(player)) {
            player.sendMessage(Component.text("Kamu masih memiliki joran CdrMoonFishing. Starter gratis hanya untuk pemain tanpa joran.", NamedTextColor.RED));
            return;
        }

        ItemStack rod = rodManager.createRod(starter.id());
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(rod);
        leftovers.values().forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage(Component.text("Kamu menerima " + starter.displayName() + " gratis.", NamedTextColor.AQUA));
    }

    private void upgradeHeldRod(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!rodManager.isProgressionRod(held)) {
            player.sendMessage(Component.text("Pegang joran CdrMoonFishing yang ingin di-upgrade di tangan utama.", NamedTextColor.RED));
            return;
        }

        RodTierDefinition current = rodManager.tier(held);
        RodTierDefinition next = rodManager.registry().nextTier(current);
        if (current == null || next == null) {
            player.sendMessage(Component.text("Joran ini sudah berada di tier maksimum.", NamedTextColor.GOLD));
            return;
        }

        double price = upgradePrice(next.id());
        if (price < 0.0) {
            player.sendMessage(Component.text("Harga upgrade ke " + next.displayName() + " belum dikonfigurasi.", NamedTextColor.RED));
            return;
        }
        if (!economy.ensureReady()) {
            player.sendMessage(Component.text("Sistem ekonomi sedang tidak tersedia.", NamedTextColor.RED));
            return;
        }
        if (economy.balance(player) + 0.0001 < price) {
            player.sendMessage(Component.text("Saldo tidak cukup. Dibutuhkan " + economy.format(price) + ".", NamedTextColor.RED));
            return;
        }

        VaultEconomyHook.WithdrawResult transaction = economy.withdraw(player, price);
        if (!transaction.success()) {
            player.sendMessage(Component.text("Upgrade gagal: " + transaction.error(), NamedTextColor.RED));
            return;
        }

        if (!rodManager.setTier(held, next.id())) {
            economy.deposit(player, price);
            player.sendMessage(Component.text("Upgrade gagal dan uangmu sudah dikembalikan.", NamedTextColor.RED));
            return;
        }

        player.getInventory().setItemInMainHand(held);
        player.sendMessage(Component.text("Upgrade berhasil: " + current.displayName() + " → " + next.displayName(), NamedTextColor.GREEN));
        player.sendMessage(Component.text("Biaya: " + economy.format(price), NamedTextColor.GRAY));
    }

    private void buyBait(Player player, String baitId) {
        BaitDefinition bait = baitRegistry.get(baitId);
        if (bait == null) return;

        double price = Math.max(0.0, config.getDouble("bait-shop." + baitId + ".price", -1.0));
        int amount = Math.max(1, config.getInt("bait-shop." + baitId + ".amount", 1));
        if (price < 0.0) return;
        if (!economy.ensureReady()) {
            player.sendMessage(Component.text("Sistem ekonomi sedang tidak tersedia.", NamedTextColor.RED));
            return;
        }
        if (economy.balance(player) + 0.0001 < price) {
            player.sendMessage(Component.text("Saldo tidak cukup untuk membeli " + bait.displayName() + ".", NamedTextColor.RED));
            return;
        }

        VaultEconomyHook.WithdrawResult transaction = economy.withdraw(player, price);
        if (!transaction.success()) {
            player.sendMessage(Component.text("Pembelian gagal: " + transaction.error(), NamedTextColor.RED));
            return;
        }

        ItemStack item = baitRegistry.createItem(bait, amount);
        Map<Integer, ItemStack> leftovers = player.getInventory().addItem(item);
        leftovers.values().forEach(leftover -> player.getWorld().dropItemNaturally(player.getLocation(), leftover));
        player.sendMessage(Component.text("Membeli " + amount + "x " + bait.displayName() + " seharga " + economy.format(price) + ".", NamedTextColor.GREEN));
    }

    private ItemStack starterButton(Player player) {
        RodTierDefinition starter = starterTier();
        ItemStack item = new ItemStack(Material.FISHING_ROD);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Joran Starter Gratis", NamedTextColor.AQUA).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text(starter == null ? "Tier starter tidak tersedia" : starter.displayName(), NamedTextColor.GRAY));
        lore.add(Component.empty());
        if (hasAnyProgressionRod(player)) {
            lore.add(Component.text("Tidak tersedia: kamu masih memiliki joran.", NamedTextColor.RED));
        } else {
            lore.add(Component.text("Klik untuk mengambil starter gratis.", NamedTextColor.GREEN));
            lore.add(Component.text("Bisa diambil lagi jika joran benar-benar hilang/patah.", NamedTextColor.DARK_GRAY));
        }
        meta.lore(clean(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack upgradeButton(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        RodTierDefinition current = rodManager.tier(held);
        RodTierDefinition next = rodManager.registry().nextTier(current);
        ItemStack item = new ItemStack(Material.ANVIL);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Upgrade Joran", NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        List<Component> lore = new ArrayList<>();

        if (current == null) {
            lore.add(Component.text("Pegang joran CdrMoonFishing di tangan utama.", NamedTextColor.RED));
        } else if (next == null) {
            lore.add(Component.text(current.displayName(), NamedTextColor.AQUA));
            lore.add(Component.text("Tier maksimum tercapai.", NamedTextColor.GOLD));
        } else {
            double price = upgradePrice(next.id());
            lore.add(Component.text(current.displayName() + " → " + next.displayName(), NamedTextColor.AQUA));
            lore.add(Component.text("Harga: " + (price < 0 ? "belum diatur" : economy.format(price)), NamedTextColor.YELLOW));
            lore.add(Component.empty());
            lore.add(Component.text("Upgrade wajib berurutan. Joran lama ditukar langsung.", NamedTextColor.GRAY));
            lore.add(Component.text("Klik untuk upgrade.", NamedTextColor.GREEN));
        }
        meta.lore(clean(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack balanceButton(Player player) {
        ItemStack item = new ItemStack(Material.GOLD_INGOT);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text("Saldo", NamedTextColor.YELLOW).decorate(TextDecoration.BOLD));
        double balance = economy.ensureReady() ? economy.balance(player) : 0.0;
        meta.lore(clean(List.of(Component.text(economy.ensureReady() ? economy.format(balance) : "Ekonomi offline", NamedTextColor.GRAY))));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack baitButton(BaitDefinition bait) {
        double price = config.getDouble("bait-shop." + bait.id() + ".price", -1.0);
        int amount = Math.max(1, config.getInt("bait-shop." + bait.id() + ".amount", 1));
        ItemStack item = new ItemStack(bait.material());
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(bait.displayName(), NamedTextColor.GOLD).decorate(TextDecoration.BOLD));
        meta.lore(clean(List.of(
                Component.text("Jumlah: " + amount, NamedTextColor.GRAY),
                Component.text("Harga: " + (price < 0 ? "belum diatur" : economy.format(price)), NamedTextColor.YELLOW),
                Component.text("Klik untuk membeli", NamedTextColor.GREEN)
        )));
        item.setItemMeta(meta);
        return item;
    }

    private void decorate(Inventory inventory) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = filler.getItemMeta();
        meta.displayName(Component.text(" "));
        filler.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, filler);
    }

    private void rebuildBaitSlots() {
        baitBySlot.clear();
        int index = 0;
        if (!config.isConfigurationSection("bait-shop")) return;
        for (String baitId : config.getConfigurationSection("bait-shop").getKeys(false)) {
            if (index >= BAIT_SLOTS.length) break;
            if (baitRegistry.get(baitId) == null) continue;
            baitBySlot.put(BAIT_SLOTS[index++], baitId.toLowerCase(Locale.ROOT));
        }
    }

    private RodTierDefinition starterTier() {
        String id = config.getString("starter.tier", "driftwood");
        RodTierDefinition tier = rodManager.registry().get(id);
        return tier == null ? rodManager.registry().firstTier() : tier;
    }

    private double upgradePrice(String tierId) {
        String path = "rod-upgrades." + tierId;
        return config.contains(path) ? Math.max(0.0, config.getDouble(path)) : -1.0;
    }

    private boolean hasAnyProgressionRod(Player player) {
        for (ItemStack item : player.getInventory().getContents()) {
            if (rodManager.isProgressionRod(item)) return true;
        }
        if (config.getBoolean("starter.scan-ender-chest", true)) {
            for (ItemStack item : player.getEnderChest().getContents()) {
                if (rodManager.isProgressionRod(item)) return true;
            }
        }
        return false;
    }

    private List<Component> clean(List<Component> lines) {
        return lines.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList();
    }

    private void reopen(Player player) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (player.isOnline()) open(player);
        });
    }

    public static final class SupplyHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
