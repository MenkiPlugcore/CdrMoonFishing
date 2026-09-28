package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.tournament.TournamentManager;
import id.menki.cdrmoonfishing.tournament.TournamentManager.RankedEntry;
import id.menki.cdrmoonfishing.tournament.TournamentMode;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FishTournamentCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 45;
    private final CdrMoonFishing plugin;
    private final TournamentManager manager;

    public FishTournamentCommand(CdrMoonFishing plugin, TournamentManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            if (sender instanceof Player player) plugin.getFishingUiManager().openTournament(player, 1);
            else sendStatus(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "top", "leaderboard" -> {
                int page = parsePage(args.length >= 2 ? args[1] : "1");
                if (sender instanceof Player player) plugin.getFishingUiManager().openTournament(player, page);
                else sendTop(sender, page);
                return true;
            }
            case "start" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage(Component.text("Usage: /" + label + " start <minutes> [points|weight|biggest]", NamedTextColor.RED));
                    return true;
                }
                int minutes;
                try {
                    minutes = Math.max(1, Math.min(1440, Integer.parseInt(args[1])));
                } catch (NumberFormatException ex) {
                    sender.sendMessage(Component.text("Minutes must be a number.", NamedTextColor.RED));
                    return true;
                }
                TournamentMode mode = args.length >= 3 ? TournamentMode.parse(args[2]) : TournamentMode.POINTS;
                if (!manager.start(minutes, mode)) {
                    sender.sendMessage(Component.text("A tournament is already active.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Tournament started.", NamedTextColor.GREEN));
                return true;
            }
            case "stop" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (!manager.stopWithRewards()) sender.sendMessage(Component.text("No active tournament.", NamedTextColor.RED));
                return true;
            }
            case "cancel" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (!manager.cancel()) sender.sendMessage(Component.text("No active tournament.", NamedTextColor.RED));
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                return true;
            }
        }
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(Component.text("━━━━━━━━ FISHING TOURNAMENT ━━━━━━━━", NamedTextColor.AQUA));
        if (!manager.isActive()) {
            sender.sendMessage(Component.text("Status: INACTIVE", NamedTextColor.GRAY));
            return;
        }
        long seconds = manager.remainingMillis() / 1000L;
        sender.sendMessage(Component.text("Status: ACTIVE", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("Mode: " + manager.mode().displayName(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Remaining: " + formatDuration(seconds), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Participants: " + manager.participantCount(), NamedTextColor.GRAY));
    }

    private void sendTop(CommandSender sender, int requestedPage) {
        if (!manager.isActive()) {
            sender.sendMessage(Component.text("No active tournament.", NamedTextColor.GRAY));
            return;
        }
        List<RankedEntry> ranking = manager.ranking();
        if (ranking.isEmpty()) {
            sender.sendMessage(Component.text("Tournament leaderboard is empty.", NamedTextColor.GRAY));
            return;
        }
        int maxPage = Math.max(1, (int) Math.ceil(ranking.size() / (double) PAGE_SIZE));
        int page = Math.max(1, Math.min(requestedPage, maxPage));
        int start = (page - 1) * PAGE_SIZE;
        int end = Math.min(ranking.size(), start + PAGE_SIZE);
        sender.sendMessage(Component.text("━━━━ TOURNAMENT TOP • " + manager.mode().displayName() + " ━━━━", NamedTextColor.AQUA));
        for (int i = start; i < end; i++) {
            RankedEntry entry = ranking.get(i);
            sender.sendMessage(Component.text("#" + entry.place() + " " + entry.name() + " • "
                    + manager.formatScore(entry.score()) + " • " + entry.catches() + " catches", NamedTextColor.GRAY));
        }
    }

    private int parsePage(String raw) {
        try { return Math.max(1, Integer.parseInt(raw)); }
        catch (NumberFormatException ignored) { return 1; }
    }

    private String formatDuration(long seconds) {
        long minutes = seconds / 60L;
        long remain = seconds % 60L;
        return String.format(Locale.US, "%02d:%02d", minutes, remain);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("status", "top"));
            if (sender.hasPermission("cdrmoonfishing.admin")) options.addAll(List.of("start", "stop", "cancel"));
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("start") && sender.hasPermission("cdrmoonfishing.admin")) {
            return List.of("10", "15", "30", "60").stream().filter(value -> value.startsWith(args[1])).toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("start") && sender.hasPermission("cdrmoonfishing.admin")) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            return List.of("points", "weight", "biggest").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
