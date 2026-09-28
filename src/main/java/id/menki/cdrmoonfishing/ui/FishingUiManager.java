package id.menki.cdrmoonfishing.ui;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.leaderboard.GlobalLeaderboardManager;
import id.menki.cdrmoonfishing.leaderboard.LeaderboardMetric;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
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
    private static final int SLOT_NEXT = 53;

    private final CdrMoonFishing plugin;

    public FishingUiManager(CdrMoonFishing plugin) {
        this.plugin = plugin;
    }

    public void openFishDex(Player player, int requestedPage) {
        List<FishDefinition> fish = sortedFish();
        int maxPage = Math.max(1, (int) Math.ceil(fish.size() / (double) PAGE_SIZE));
        int page = clampPage(requestedPage, maxPage);
        Inventory inventory = Bukkit.createInventory(new FishDexHolder(page), 54,
                Component.text("FishDex • Page " + page + "/" + maxPage, NamedTextColor.DARK_AQUA));

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(fish.size(), start + PAGE_SIZE);
        for (int index = start; index < end; index++) {
            FishDefinition definition = fish.get(index);
            FishDexEntry entry = plugin.getPlayerStatsManager().entry(player, definition.id());
            inventory.setItem(index - start, fishDexCard(definition, entry));
        }

        fillNavigation(inventory, page, maxPage);
        StatsSnapshot stats = plugin.getPlayerStatsManager().snapshot(player);
        int discovered = activeDiscovered(player);
        double completion = fish.isEmpty() ? 0.0 : discovered * 100.0 / fish.size();
        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Discovered: " + discovered + "/" + fish.size(), NamedTextColor.GRAY));
        lore.add(Component.text(String.format(Locale.US, "Completion: %.1f%%", completion), NamedTextColor.AQUA));
        lore.add(Component.text(String.format(Locale.US, "Collection Luck: +%.0f%%",
                plugin.getMilestoneManager().collectionLuck(player) * 100.0), NamedTextColor.LIGHT_PURPLE));
        lore.add(Component.text("Click to view milestone progress.", NamedTextColor.DARK_GRAY));
        inventory.setItem(SLOT_INFO, button(Material.KNOWLEDGE_BOOK, "FishDex Progress", NamedTextColor.AQUA, lore));
        player.openInventory(inventory);
    }

    public void openLeaderboard(Player player, LeaderboardMetric metric, int requestedPage) {
        GlobalLeaderboardManager manager = plugin.getGlobalLeaderboardManager();
        List<GlobalLeaderboardManager.Entry> ranking = manager.ranking(metric);
        int maxPage = Math.max(1, (int) Math.ceil(ranking.size() / (double) PAGE_SIZE));
        int page = clampPage(requestedPage, maxPage);
        Inventory inventory = Bukkit.createInventory(new LeaderboardHolder(metric, page), 54,
                Component.text("Fishing Leaderboard • " + metric.displayName(), NamedTextColor.DARK_AQUA));

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
        inventory.setItem(SLOT_INFO, button(Material.COMPASS, "Lifetime Rankings", NamedTextColor.AQUA,
                List.of(Component.text("Click a metric below to switch leaderboard.", NamedTextColor.GRAY))));
        player.openInventory(inventory);
    }

    public void openTournament(Player player, int requestedPage) {
        TournamentManager manager = plugin.getTournamentManager();
        List<RankedEntry> ranking = manager.isActive() ? manager.ranking() : List.of();
        int maxPage = Math.max(1, (int) Math.ceil(ranking.size() / (double) PAGE_SIZE));
        int page = clampPage(requestedPage, maxPage);
        Inventory inventory = Bukkit.createInventory(new TournamentHolder(page), 54,
                Component.text("Fishing Tournament", NamedTextColor.DARK_AQUA));

        if (!manager.isActive()) {
            inventory.setItem(22, button(Material.CLOCK, "No Active Tournament", NamedTextColor.GRAY,
                    List.of(Component.text("An admin can start one with /fishtournament start.", NamedTextColor.DARK_GRAY))));
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
                                Component.text("Score: " + manager.formatScore(entry.score()), NamedTextColor.WHITE),
                                Component.text("Catches: " + entry.catches(), NamedTextColor.GRAY)
                        )));
            }
        }

        fillNavigation(inventory, page, maxPage);
        List<Component> info = new ArrayList<>();
        info.add(Component.text("Status: " + (manager.isActive() ? "ACTIVE" : "INACTIVE"),
                manager.isActive() ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        if (manager.isActive()) {
            info.add(Component.text("Mode: " + manager.mode().displayName(), NamedTextColor.GRAY));
            info.add(Component.text("Remaining: " + formatDuration(manager.remainingMillis() / 1000L), NamedTextColor.GRAY));
            info.add(Component.text("Participants: " + manager.participantCount(), NamedTextColor.GRAY));
        }
        inventory.setItem(SLOT_INFO, button(Material.CLOCK, "Tournament Status", NamedTextColor.AQUA, info));
        player.openInventory(inventory);
    }

    public boolean isManaged(Inventory inventory) {
        return inventory != null && (inventory.getHolder() instanceof FishDexHolder
                || inventory.getHolder() instanceof LeaderboardHolder
                || inventory.getHolder() instanceof TournamentHolder);
    }

    public void handleClick(Player player, Inventory inventory, int rawSlot) {
        if (inventory.getHolder() instanceof FishDexHolder holder) {
            if (rawSlot == SLOT_PREV) openFishDex(player, holder.page() - 1);
            else if (rawSlot == SLOT_NEXT) openFishDex(player, holder.page() + 1);
            else if (rawSlot == SLOT_INFO) {
                player.closeInventory();
                plugin.getMilestoneManager().sendStatus(player);
            }
            return;
        }

        if (inventory.getHolder() instanceof LeaderboardHolder holder) {
            if (rawSlot == SLOT_PREV) openLeaderboard(player, holder.metric(), holder.page() - 1);
            else if (rawSlot == SLOT_NEXT) openLeaderboard(player, holder.metric(), holder.page() + 1);
            else if (rawSlot == 46) openLeaderboard(player, LeaderboardMetric.CATCHES, 1);
            else if (rawSlot == 47) openLeaderboard(player, LeaderboardMetric.WEIGHT, 1);
            else if (rawSlot == 48) openLeaderboard(player, LeaderboardMetric.BIGGEST, 1);
            else if (rawSlot == 50) openLeaderboard(player, LeaderboardMetric.LEGENDARY, 1);
            return;
        }

        if (inventory.getHolder() instanceof TournamentHolder holder) {
            if (rawSlot == SLOT_PREV) openTournament(player, holder.page() - 1);
            else if (rawSlot == SLOT_NEXT) openTournament(player, holder.page() + 1);
        }
    }

    private ItemStack fishDexCard(FishDefinition fish, FishDexEntry entry) {
        if (!entry.discovered()) {
            return button(Material.GRAY_STAINED_GLASS_PANE, "???", NamedTextColor.DARK_GRAY,
                    List.of(Component.text("Catch this species to reveal it.", NamedTextColor.GRAY)));
        }

        List<Component> lore = new ArrayList<>();
        lore.add(Component.text("Rarity: " + fish.rarity().displayName(), rarityColor(fish.rarity())));
        lore.add(Component.text("Caught: " + entry.count(), NamedTextColor.GRAY));
        lore.add(Component.text(String.format(Locale.US, "Best: %.2f kg", entry.bestWeight()), NamedTextColor.GREEN));
        lore.add(Component.text("Depth: " + fish.minDepth() + "–" + fish.maxDepth(), NamedTextColor.DARK_GRAY));
        if (!fish.requiredBaits().isEmpty()) {
            lore.add(Component.text("Required bait: " + String.join(", ", fish.requiredBaits()), NamedTextColor.GOLD));
        }
        return button(fish.material(), fish.displayName(), rarityColor(fish.rarity()), lore);
    }

    private ItemStack metricButton(LeaderboardMetric metric, LeaderboardMetric selected) {
        boolean active = metric == selected;
        Material material = active ? Material.LIME_DYE : Material.GRAY_DYE;
        return button(material, metric.displayName(), active ? NamedTextColor.GREEN : NamedTextColor.GRAY,
                List.of(Component.text(active ? "Selected" : "Click to view", NamedTextColor.DARK_GRAY)));
    }

    private void fillNavigation(Inventory inventory, int page, int maxPage) {
        inventory.setItem(SLOT_PREV, button(Material.ARROW, "Previous Page", NamedTextColor.AQUA,
                List.of(Component.text("Page " + Math.max(1, page - 1), NamedTextColor.GRAY))));
        inventory.setItem(SLOT_NEXT, button(Material.ARROW, "Next Page", NamedTextColor.AQUA,
                List.of(Component.text("Page " + Math.min(maxPage, page + 1), NamedTextColor.GRAY))));
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
