package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.leaderboard.GlobalLeaderboardManager;
import id.menki.cdrmoonfishing.leaderboard.GlobalLeaderboardManager.Entry;
import id.menki.cdrmoonfishing.leaderboard.LeaderboardMetric;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class FishLeaderboardCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 45;
    private final CdrMoonFishing plugin;
    private final GlobalLeaderboardManager manager;

    public FishLeaderboardCommand(CdrMoonFishing plugin, GlobalLeaderboardManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        LeaderboardMetric metric = args.length >= 1 ? LeaderboardMetric.parse(args[0]) : LeaderboardMetric.CATCHES;
        int requestedPage = 1;
        if (args.length >= 2) {
            try {
                requestedPage = Math.max(1, Integer.parseInt(args[1]));
            } catch (NumberFormatException ignored) {
                sender.sendMessage(Component.text("Page must be a number.", NamedTextColor.RED));
                return true;
            }
        }

        if (sender instanceof Player player) {
            plugin.getFishingUiManager().openLeaderboard(player, metric, requestedPage);
            return true;
        }

        List<Entry> ranking = manager.ranking(metric);
        if (ranking.isEmpty()) {
            sender.sendMessage(Component.text("Fishing leaderboard is empty.", NamedTextColor.GRAY));
            return true;
        }

        int maxPage = Math.max(1, (int) Math.ceil(ranking.size() / (double) PAGE_SIZE));
        int page = Math.min(requestedPage, maxPage);
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(ranking.size(), start + PAGE_SIZE);
        sender.sendMessage(Component.text("━━━━ GLOBAL FISHING • " + metric.displayName().toUpperCase(Locale.ROOT) + " ━━━━", NamedTextColor.AQUA));
        for (int i = start; i < end; i++) {
            Entry entry = ranking.get(i);
            sender.sendMessage(Component.text("#" + (i + 1) + " " + entry.name() + " • " + manager.formatValue(metric, entry), NamedTextColor.GRAY));
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("catches", "weight", "biggest", "legendary").stream()
                    .filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
