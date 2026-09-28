package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.model.FishDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.FishDexEntry;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.StatsSnapshot;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class FishDexCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 7;
    private final CdrMoonFishing plugin;

    public FishDexCommand(CdrMoonFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is player-only.", NamedTextColor.RED));
            return true;
        }

        int requestedPage = 1;
        if (args.length >= 1) {
            try {
                requestedPage = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Page must be a number.", NamedTextColor.RED));
                return true;
            }
        }

        List<FishDefinition> fish = new ArrayList<>(plugin.getFishRegistry().definitions().values());
        fish.sort(Comparator
                .comparingInt((FishDefinition definition) -> definition.rarity().ordinal())
                .thenComparing(FishDefinition::displayName, String.CASE_INSENSITIVE_ORDER));

        if (fish.isEmpty()) {
            sender.sendMessage(Component.text("FishDex is empty because no fish are registered.", NamedTextColor.GRAY));
            return true;
        }

        int maxPage = Math.max(1, (int) Math.ceil(fish.size() / (double) PAGE_SIZE));
        int page = Math.min(requestedPage, maxPage);
        StatsSnapshot stats = plugin.getPlayerStatsManager().snapshot(player);
        double completion = fish.isEmpty() ? 0.0 : (stats.discoveredSpecies() * 100.0 / fish.size());

        sender.sendMessage(Component.text("━━━━━━━━━━ FISHDEX ━━━━━━━━━━", NamedTextColor.AQUA));
        sender.sendMessage(Component.text(String.format(Locale.US,
                "Discovered %d/%d • %.1f%% • Page %d/%d",
                stats.discoveredSpecies(), fish.size(), completion, page, maxPage), NamedTextColor.GRAY));

        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(fish.size(), start + PAGE_SIZE);
        for (int i = start; i < end; i++) {
            FishDefinition definition = fish.get(i);
            FishDexEntry entry = plugin.getPlayerStatsManager().entry(player, definition.id());
            if (!entry.discovered()) {
                sender.sendMessage(Component.text("□ ???", NamedTextColor.DARK_GRAY));
                continue;
            }

            Component line = Component.text("✔ ", NamedTextColor.GREEN)
                    .append(Component.text(definition.displayName(), rarityColor(definition.rarity())))
                    .append(Component.text(String.format(Locale.US,
                            "  • x%d • Best %.2f kg",
                            entry.count(), entry.bestWeight()), NamedTextColor.GRAY));
            sender.sendMessage(line);
        }

        if (maxPage > 1) {
            sender.sendMessage(Component.text("Use /" + label + " <page> to browse.", NamedTextColor.DARK_GRAY));
        }
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        int fishCount = plugin.getFishRegistry().definitions().size();
        int maxPage = Math.max(1, (int) Math.ceil(fishCount / (double) PAGE_SIZE));
        String prefix = args[0];
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= maxPage; page++) {
            String value = Integer.toString(page);
            if (value.startsWith(prefix)) {
                pages.add(value);
            }
        }
        return pages;
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
}
