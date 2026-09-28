package id.menki.cdrmoonfishing.ui;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.contracts.ContractManager.ContractProgress;
import id.menki.cdrmoonfishing.leaderboard.GlobalLeaderboardManager;
import id.menki.cdrmoonfishing.leaderboard.LeaderboardMetric;
import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.model.RodTierDefinition;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.FishDexEntry;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.StatsSnapshot;
import id.menki.cdrmoonfishing.tournament.TournamentManager;
import id.menki.cdrmoonfishing.tournament.TournamentManager.RankedEntry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FishingUiManager {
    private static final int PAGE_SIZE = 45;
    private static final int SLOT_PREV = 45;
    private static final int SLOT_INFO = 49;
    private static final int SLOT_HOME = 52;
    private static final int SLOT_NEXT = 53;

    private static final int HUB_FISHDEX = 10;
    private static final int HUB_ROD = 11;
    private static final int HUB_CONTRACTS = 12;
    private static final int HUB_STATUS = 13;
    private static final int HUB_MARKET = 14;
    private static final int HUB_TOURNAMENT = 15;
    private static final int HUB_STATS = 16;
    private static final int HUB_REFRESH = 21;
    private static final int HUB_CLOSE = 23;

    private final CdrMoonFishing plugin;

    public FishingUiManager(CdrMoonFishing plugin) {
        this.plugin = plugin;
    }

    public void openHub(Player player) {
        Inventory inventory = Bukkit.createInventory(new HubHolder(), 27,
                Component.text("☾ CdrMoonFishing", NamedTextColor.DARK_AQUA));

        ItemStack dark = filler(Material.BLACK_STAINED_GLASS_PANE);
        ItemStack blue = filler(Material.BLUE_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, dark);
        for (int slot = 0; slot < 9; slot++) inventory.setItem(slot, blue);
        for (int slot = 18; slot < 27; slot++) inventory.setItem(slot, blue);

        StatsSnapshot stats = plugin.getPlayerStatsManager().snapshot(player);
        int totalFish = plugin.getFishRegistry().definitions().size();
        int discovered = activeDiscovered(player);
        double completion = totalFish == 0 ? 0.0 : discovered * 100.0 / totalFish;
        double collectionLuck = plugin.getMilestoneManager().collectionLuck(player);

        inventory.setItem(4, button(Material.HEART_OF_THE_SEA, "Moon Fishing", NamedTextColor.AQUA,
                List.of(
                        Component.text("Pusat menu memancing", NamedTextColor.GRAY),
                        Component.text("Aman untuk Java + Bedrock", NamedTextColor.DARK_GRAY)
                )));

        inventory.setItem(HUB_FISHDEX, button(Material.KNOWLEDGE_BOOK, "FishDex", NamedTextColor.AQUA,
                List.of(
                        Component.text("Koleksi " + discovered + "/" + totalFish, NamedTextColor.GRAY),
                        Component.text(String.format(Locale.US, "%.1f%% selesai", completion), NamedTextColor.GREEN),
                        Component.text(String.format(Locale.US, "+%.0f%% Luck Koleksi", collectionLuck * 100.0), NamedTextColor.LIGHT_PURPLE),
                        Component.text("Klik untuk membuka FishDex", NamedTextColor.DARK_GRAY)
                )));

        ItemStack heldRod = player.getInventory().getItemInMainHand();
        RodTierDefinition tier = plugin.getRodManager().tier(heldRod);
        List<Component> rodLore = new ArrayList<>();
        if (tier == null) {
            rodLore.add(Component.text("Tidak memegang joran progres", NamedTextColor.GRAY));
            rodLore.add(Component.text("Pegang joran CdrMoonFishing", NamedTextColor.DARK_GRAY));
        } else {
            int xp = plugin.getRodManager().xp(heldRod);
            RodTierDefinition next = plugin.getRodRegistry().nextTier(tier);
            rodLore.add(Component.text("Tingkat: " + tier.displayName(), NamedTextColor.AQUA));
            rodLore.add(Component.text("XP: " + xp + (next == null ? " • MAKS" : " / " + next.minXp()), NamedTextColor.GRAY));
            rodLore.add(Component.text(String.format(Locale.US, "Luck +%.0f%% • Tarik %.2fx",
                    tier.rarityLuck() * 100.0, tier.reelMultiplier()), NamedTextColor.LIGHT_PURPLE));
        }
        rodLore.add(Component.text("Klik untuk detail joran", NamedTextColor.DARK_GRAY));
        inventory.setItem(HUB_ROD, button(Material.FISHING_ROD, "Joran Pancing", NamedTextColor.AQUA, rodLore));

        List<ContractProgress> contracts = plugin.getContractManager().progress(player);
        long completedContracts = contracts.stream().filter(ContractProgress::completed).count();
        List<Component> contractLore = new ArrayList<>();
        contractLore.add(Component.text("Hari ini: " + completedContracts + "/" + contracts.size() + " selesai",
                completedContracts == contracts.size() && !contracts.isEmpty() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        for (ContractProgress progress : contracts.stream().limit(2).toList()) {
            String marker = progress.completed() ? "✔ " : "• ";
            contractLore.add(Component.text(marker + progress.definition().displayName(),
                    progress.completed() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        }
        contractLore.add(Component.text("Klik untuk melihat kontrak", NamedTextColor.DARK_GRAY));
        inventory.setItem(HUB_CONTRACTS, button(Material.WRITABLE_BOOK, "Kontrak Harian", NamedTextColor.GOLD, contractLore));

        BaitDefinition selectedBait = plugin.getBaitManager().selected(player);
        FishDefinition featured = plugin.getFishMarketManager().featuredFish();
        List<Component> statusLore = new ArrayList<>();
        statusLore.add(Component.text("Pemain: " + player.getName(), NamedTextColor.WHITE));
        statusLore.add(Component.text(String.format(Locale.US, "FishDex: %.1f%%", completion), NamedTextColor.AQUA));
        statusLore.add(Component.text("Umpan: " + (selectedBait == null ? "Tidak Ada" : selectedBait.displayName()),
                selectedBait == null ? NamedTextColor.GRAY : NamedTextColor.GOLD));
        statusLore.add(Component.text("Unggulan: " + (featured == null ? "Tidak Ada" : featured.displayName()),
                featured == null ? NamedTextColor.GRAY : NamedTextColor.LIGHT_PURPLE));
        inventory.setItem(HUB_STATUS, button(Material.NAUTILUS_SHELL, "Status Memancing", NamedTextColor.WHITE, statusLore));

        List<Component> marketLore = new ArrayList<>();
        if (featured != null) {
            double multiplier = Math.max(1.0, plugin.getConfig().getDouble("economy.market.featured-multiplier", 1.35));
            marketLore.add(Component.text("Unggulan: " + featured.displayName(), NamedTextColor.GOLD));
            marketLore.add(Component.text(String.format(Locale.US, "Bonus pasar x%.2f", multiplier), NamedTextColor.GREEN));
        } else {
            marketLore.add(Component.text("Tidak ada ikan unggulan hari ini", NamedTextColor.GRAY));
        }
        marketLore.add(Component.text("Klik untuk pergi ke pasar", NamedTextColor.DARK_GRAY));
        inventory.setItem(HUB_MARKET, button(Material.EMERALD, "Pasar Ikan", NamedTextColor.GREEN, marketLore));

        TournamentManager tournament = plugin.getTournamentManager();
        List<Component> tournamentLore = new ArrayList<>();
        if (tournament.isActive()) {
            tournamentLore.add(Component.text("AKTIF • " + tournament.mode().displayName(), NamedTextColor.GREEN));
            tournamentLore.add(Component.text("Sisa waktu: " + formatDuration(tournament.remainingMillis() / 1000L), NamedTextColor.GRAY));
            tournamentLore.add(Component.text("Peserta: " + tournament.participantCount(), NamedTextColor.GRAY));
        } else tournamentLore.add(Component.text("Tidak ada turnamen aktif", NamedTextColor.GRAY));
        tournamentLore.add(Component.text("Klik untuk membuka turnamen", NamedTextColor.DARK_GRAY));
        inventory.setItem(HUB_TOURNAMENT, button(Material.GOLD_BLOCK, "Turnamen", NamedTextColor.GOLD, tournamentLore));

        List<Component> statsLore = new ArrayList<>();
        statsLore.add(Component.text("Total tangkapan: " + stats.totalCatches(), NamedTextColor.GRAY));
        statsLore.add(Component.text(String.format(Locale.US, "Total berat: %.2f kg", stats.totalWeight()), NamedTextColor.GRAY));
        if (stats.biggestWeight() > 0.0) {
            statsLore.add(Component.text(String.format(Locale.US, "Terbesar: %.2f kg", stats.biggestWeight()), NamedTextColor.GREEN));
        }
        statsLore.add(Component.text("Klik untuk peringkat global", NamedTextColor.DARK_GRAY));
        inventory.setItem(HUB_STATS, button(Material.COMPASS, "Statistik & Peringkat", NamedTextColor.AQUA, statsLore));

        inventory.setItem(HUB_REFRESH, button(Material.CLOCK, "Segarkan", NamedTextColor.AQUA,
                List.of(Component.text("Perbarui data menu", NamedTextColor.GRAY))));
        inventory.setItem(HUB_CLOSE, button(Material.BARRIER, "Tutup", NamedTextColor.RED,
                List.of(Component.text("Tutup menu memancing", NamedTextColor.GRAY))));

        player.openInventory(inventory);
    }

    public void openFishDex(Player player, int requestedPage) {
        List<FishDefinition> fish = sortedFish();
        int maxPage = Math.max(1, (int) Math.ceil(fish.size() / (double) PAGE_SIZE));
        int page = clampPage(requestedPage, maxPage);
        Inventory inventory = Bukkit.createInventory(new FishDexHolder(page), 54,
                Component.text("FishDex • Halaman " + page + "/" + maxPage, NamedTextColor.DARK_AQUA));

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(fish.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            FishDefinition definition = fish.get(index);
            FishDexEntry entry = plugin.getPlayerStatsManager().entry(player, definition.id());
            inventory.setItem(index - start, fishDexCard(definition, entry));
        }

        fillNavigation(inventory, page, maxPage);
        int discovered = activeDiscovered(player);
        double completion = fish.isEmpty() ? 0.0 : discovered * 100.0 / fish.size();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Ditemukan: " + discovered + "/" + fish.size(), NamedTextColor.GRAY));
        lore.add(Component.text(String.format(Locale.US, "Penyelesaian: %.1f%%", completion), NamedTextColor.AQUA));
        lore.add(Component.text(String.format(Locale.US, "Luck Koleksi: +%.0f%%",
                plugin.getMilestoneManager().collectionLuck(player) * 100.0), NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.text("Klik untuk melihat progres milestone.", NamedTextColor.DARK_GRAY));
        inventory.setItem(SLOT_INFO, button(Material.KNOWLEDGE_BOOK, "Progres FishDex", NamedTextColor.AQUA, lore));
        inventory.setItem(SLOT_HOME, homeButton());
        player.openInventory(inventory);
    }

    public void openLeaderboard(Player player, LeaderboardMetric metric, int requestedPage) {
        GlobalLeaderboardManager manager = plugin.getGlobalLeaderboardManager();
        List<GlobalLeaderboardManager.Entry> ranking = manager.ranking(metric);
        int maxPage = Math.max(1, (int) Math.ceil(ranking.size() / (double) PAGE_SIZE));
        int page = clampPage(requestedPage, maxPage);
        Inventory inventory = Bukkit.createInventory(new LeaderboardHolder(metric, page), 54,
                Component.text("Peringkat Memancing • " + metric.displayName(), NamedTextColor.DARK_AQUA));

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(ranking.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            GlobalLeaderboardManager.Entry entry = ranking.get(index);
            int place = index + 1;
            Material material = place == 1 ? Material.GOLD_INGOT : place <= 3 ? Material.IRON_INGOT : Material.PAPER;
            NamedTextColor color = place == 1 ? NamedTextColor.GOLD : place <= 3 ? NamedTextColor.AQUA : NamedTextColor.GRAY;
            inventory.setItem(index - start, button(material, "#" + place + " " + entry.name(), color,
                    List.of(Component.text(manager.formatValue(metric, entry), NamedTextColor.WHITE))));
        }

        fillNavigation(inventory, page, maxPage);
        inventory.setItem(46, metricButton(LeaderboardMetric.CATCHES, metric));
        inventory.setItem(47, metricButton(LeaderboardMetric.WEIGHT, metric));
        inventory.setItem(48, metricButton(LeaderboardMetric.BIGGEST, metric));
        inventory.setItem(50, metricButton(LeaderboardMetric.LEGENDARY, metric));
        inventory.setItem(SLOT_INFO, button(Material.COMPASS, "Peringkat Sepanjang Masa", NamedTextColor.AQUA,
                List.of(Component.text("Klik kategori di bawah untuk mengganti peringkat.", NamedTextColor.GRAY))));
        inventory.setItem(SLOT_HOME, homeButton());
        player.openInventory(inventory);
    }

    public void openTournament(Player player, int requestedPage) {
        TournamentManager manager = plugin.getTournamentManager();
        List<RankedEntry> ranking = manager.isActive() ? manager.ranking() : List.of();
        int maxPage = Math.max(1, (int) Math.ceil(ranking.size() / (double) PAGE_SIZE));
        int page = clampPage(requestedPage, maxPage);
        Inventory inventory = Bukkit.createInventory(new TournamentHolder(page), 54,
                Component.text("Turnamen Memancing", NamedTextColor.DARK_AQUA));

        if (!manager.isActive()) {
            inventory.setItem(22, button(Material.CLOCK, "Tidak Ada Turnamen Aktif", NamedTextColor.GRAY,
                    List.of(Component.text("Admin dapat memulai dengan /fishtournament start.", NamedTextColor.DARK_GRAY))));
        } else {
            int start = (page - 1) * PAGE_SIZE;
            int end = Math.min(ranking.size(), start + PAGE_SIZE);
            for (int index = start; index < end; index++) {
                RankedEntry entry = ranking.get(index);
                Material material = entry.place() == 1 ? Material.DIAMOND : entry.place() <= 3 ? Material.EMERALD : Material.PAPER;
                NamedTextColor color = entry.place() == 1 ? NamedTextColor.GOLD
                        : entry.place() <= 3 ? NamedTextColor.AQUA : NamedTextColor.GRAY;
                inventory.setItem(index - start, button(material, "#" + entry.place() + " " + entry.name(), color,
                        List.of(
                                Component.text("Skor: " + manager.formatScore(entry.score()), NamedTextColor.WHITE),
                                Component.text("Tangkapan: " + entry.catches(), NamedTextColor.GRAY)
                        )));
            }
        }

        fillNavigation(inventory, page, maxPage);
        List<Component> info = new ArrayList<>();
        info.add(Component.text("Status: " + (manager.isActive() ? "AKTIF" : "TIDAK AKTIF"),
                manager.isActive() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        if (manager.isActive()) {
            info.add(Component.text("Mode: " + manager.mode().displayName(), NamedTextColor.GRAY));
            info.add(Component.text("Sisa waktu: " + formatDuration(manager.remainingMillis() / 1000L), NamedTextColor.GRAY));
            info.add(Component.text("Peserta: " + manager.participantCount(), NamedTextColor.GRAY));
        }
        inventory.setItem(SLOT_INFO, button(Material.CLOCK, "Status Turnamen", NamedTextColor.AQUA, info));
        inventory.setItem(SLOT_HOME, homeButton());
        player.openInventory(inventory);
    }

    public boolean isManaged(Inventory inventory) {
        return inventory != null && (inventory.getHolder() instanceof HubHolder
                || inventory.getHolder() instanceof FishDexHolder
                || inventory.getHolder() instanceof LeaderboardHolder
                || inventory.getHolder() instanceof TournamentHolder);
    }

    public void handleClick(Player player, Inventory inventory, int rawSlot) {
        if (inventory.getHolder() instanceof HubHolder) {
            switch (rawSlot) {
                case HUB_FISHDEX -> openFishDex(player, 1);
                case HUB_ROD -> {
                    player.closeInventory();
                    player.performCommand("fishrod info");
                }
                case HUB_CONTRACTS -> {
                    player.closeInventory();
                    player.performCommand("fishcontracts");
                }
                case HUB_MARKET -> plugin.getFishMarketManager().open(player);
                case HUB_TOURNAMENT -> openTournament(player, 1);
                case HUB_STATS -> openLeaderboard(player, LeaderboardMetric.CATCHES, 1);
                case HUB_REFRESH -> openHub(player);
                case HUB_CLOSE -> player.closeInventory();
                default -> { }
            }
            return;
        }

        if (inventory.getHolder() instanceof FishDexHolder holder) {
            if (rawSlot == SLOT_PREV) openFishDex(player, holder.page() - 1);
            else if (rawSlot == SLOT_NEXT) openFishDex(player, holder.page() + 1);
            else if (rawSlot == SLOT_HOME) openHub(player);
            else if (rawSlot == SLOT_INFO) {
                player.closeInventory();
                plugin.getMilestoneManager().sendStatus(player);
            }
            return;
        }

        if (inventory.getHolder() instanceof LeaderboardHolder holder) {
            if (rawSlot == SLOT_PREV) openLeaderboard(player, holder.metric(), holder.page() - 1);
            else if (rawSlot == SLOT_NEXT) openLeaderboard(player, holder.metric(), holder.page() + 1);
            else if (rawSlot == SLOT_HOME) openHub(player);
            else if (rawSlot == 46) openLeaderboard(player, LeaderboardMetric.CATCHES, 1);
            else if (rawSlot == 47) openLeaderboard(player, LeaderboardMetric.WEIGHT, 1);
            else if (rawSlot == 48) openLeaderboard(player, LeaderboardMetric.BIGGEST, 1);
            else if (rawSlot == 50) openLeaderboard(player, LeaderboardMetric.LEGENDARY, 1);
            return;
        }

        if (inventory.getHolder() instanceof TournamentHolder holder) {
            if (rawSlot == SLOT_PREV) openTournament(player, holder.page() - 1);
            else if (rawSlot == SLOT_NEXT) openTournament(player, holder.page() + 1);
            else if (rawSlot == SLOT_HOME) openHub(player);
        }
    }

    private ItemStack fishDexCard(FishDefinition fish, FishDexEntry entry) {
        if (!entry.discovered()) {
            return button(Material.GRAY_STAINED_GLASS_PANE, "???", NamedTextColor.DARK_GRAY,
                    List.of(Component.text("Tangkap spesies ini untuk membukanya.", NamedTextColor.GRAY)));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Kelangkaan: " + fish.rarity().displayName(), rarityColor(fish.rarity())));
        lore.add(Component.text("Ditangkap: " + entry.count(), NamedTextColor.GRAY));
        lore.add(Component.text(String.format(Locale.US, "Terbaik: %.2f kg", entry.bestWeight()), NamedTextColor.GREEN));
        lore.add(Component.text("Kedalaman: " + fish.minDepth() + "–" + fish.maxDepth(), NamedTextColor.DARK_GRAY));
        if (!fish.requiredBaits().isEmpty()) {
            lore.add(Component.text("Umpan wajib: " + String.join(", ", fish.requiredBaits()), NamedTextColor.GOLD));
        }
        return button(fish.material(), fish.displayName(), rarityColor(fish.rarity()), lore);
    }

    private ItemStack metricButton(LeaderboardMetric metric, LeaderboardMetric selected) {
        boolean active = metric == selected;
        Material material = active ? Material.LIME_DYE : Material.GRAY_DYE;
        return button(material, metric.displayName(), active ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(Component.text(active ? "Dipilih" : "Klik untuk melihat", NamedTextColor.DARK_GRAY)));
    }

    private void fillNavigation(Inventory inventory, int page, int maxPage) {
        inventory.setItem(SLOT_PREV, button(Material.ARROW, "Halaman Sebelumnya", NamedTextColor.AQUA,
                List.of(Component.text("Halaman " + Math.max(1, page - 1), NamedTextColor.GRAY))));
        inventory.setItem(SLOT_NEXT, button(Material.ARROW, "Halaman Berikutnya", NamedTextColor.AQUA,
                List.of(Component.text("Halaman " + Math.min(maxPage, page + 1), NamedTextColor.GRAY))));
    }

    private ItemStack homeButton() {
        return button(Material.HEART_OF_THE_SEA, "Menu Memancing", NamedTextColor.AQUA,
                List.of(Component.text("Kembali ke menu utama", NamedTextColor.GRAY)));
    }

    private ItemStack filler(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.empty());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack button(Material material, String name, NamedTextColor color, List<Component> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(Component.text(name, color).decoration(TextDecoration.ITALIC, false));
        if (!lore.isEmpty()) {
            meta.lore(lore.stream().map(line -> line.decoration(TextDecoration.ITALIC, false)).toList());
        }
        item.setItemMeta(meta);
        return item;
    }

    private List<FishDefinition> sortedFish() {
        List<FishDefinition> fish = new ArrayList<>(plugin.getFishRegistry().definitions().values());
        fish.sort(Comparator.comparingInt((FishDefinition value) -> value.rarity().ordinal())
                .thenComparing(FishDefinition::displayName, String.CASE_INSENSITIVE_ORDER));
        return fish;
    }

    private int activeDiscovered(Player player) {
        int count = 0;
        for (FishDefinition fish : plugin.getFishRegistry().definitions().values()) {
            if (plugin.getPlayerStatsManager().entry(player, fish.id()).discovered()) count++;
        }
        return count;
    }

    private NamedTextColor rarityColor(FishRarity rarity) {
        return switch (rarity) {
            case COMMON -> NamedTextColor.WHITE;
            case UNCOMMON -> NamedTextColor.GREEN;
            case RARE -> NamedTextColor.AQUA;
            case EPIC -> NamedTextColor.LIGHT_PURPLE;
            case LEGENDARY -> NamedTextColor.GOLD;
        };
    }

    private int clampPage(int requestedPage, int maxPage) {
        return Math.max(1, Math.min(Math.max(1, requestedPage), maxPage));
    }

    private String formatDuration(long seconds) {
        long minutes = Math.max(0, seconds) / 60L;
        long remain = Math.max(0, seconds) % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, remain);
    }

    public record HubHolder() implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public record FishDexHolder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public record LeaderboardHolder(LeaderboardMetric metric, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public record TournamentHolder(int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
