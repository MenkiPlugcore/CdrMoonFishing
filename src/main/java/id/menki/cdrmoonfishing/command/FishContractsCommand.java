package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.contracts.ContractDefinition;
import id.menki.cdrmoonfishing.contracts.ContractManager;
import id.menki.cdrmoonfishing.contracts.ContractManager.ContractProgress;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FishContractsCommand implements CommandExecutor, TabCompleter {
    private final ContractManager manager;

    public FishContractsCommand(ContractManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("cdrmoonfishing.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            manager.reload();
            sender.sendMessage(Component.text("Daily fishing contracts reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("reset")) {
            if (!sender.hasPermission("cdrmoonfishing.admin")) {
                sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(Component.text("Usage: /fishcontracts reset <player>", NamedTextColor.YELLOW));
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player must be online.", NamedTextColor.RED));
                return true;
            }
            manager.reset(target);
            sender.sendMessage(Component.text("Reset today's contracts for " + target.getName() + ".", NamedTextColor.GREEN));
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players: /fishcontracts | Admin: /fishcontracts reload | reset <player>");
            return true;
        }

        show(player);
        return true;
    }

    private void show(Player player) {
        List<ContractProgress> contracts = manager.progress(player);
        long millis = manager.millisUntilReset();
        long hours = millis / 3_600_000L;
        long minutes = (millis % 3_600_000L) / 60_000L;

        player.sendMessage(Component.text("━━━━━━━━ DAILY FISHING CONTRACTS ━━━━━━━━", NamedTextColor.AQUA));
        player.sendMessage(Component.text("Date: " + manager.currentDate() + " • Reset in " + hours + "h " + minutes + "m", NamedTextColor.DARK_GRAY));

        if (contracts.isEmpty()) {
            player.sendMessage(Component.text("No daily contracts are configured.", NamedTextColor.GRAY));
            return;
        }

        int index = 1;
        for (ContractProgress progress : contracts) {
            ContractDefinition definition = progress.definition();
            String value = formatProgress(definition, progress.progress());
            NamedTextColor color = progress.completed() ? NamedTextColor.GREEN : NamedTextColor.WHITE;
            String marker = progress.completed() ? "✓" : "•";

            player.sendMessage(Component.text(marker + " [" + index + "] " + definition.displayName(), color));
            player.sendMessage(Component.text("  " + definition.description(), NamedTextColor.GRAY));
            player.sendMessage(Component.text("  Progress: " + value, progress.completed() ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            player.sendMessage(Component.text("  Reward: " + manager.rewardSummary(definition), NamedTextColor.GOLD));
            index++;
        }
    }

    private String formatProgress(ContractDefinition definition, double progress) {
        if (definition.progressMode() == ContractDefinition.ProgressMode.WEIGHT) {
            return String.format(Locale.US, "%.2f / %.2f kg", progress, definition.target());
        }
        return (int) Math.floor(progress + 0.0001) + " / " + (int) Math.ceil(definition.target());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("cdrmoonfishing.admin")) {
            return filter(List.of("reload", "reset"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("reset") && sender.hasPermission("cdrmoonfishing.admin")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String prefix) {
        String lowered = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lowered)) result.add(value);
        }
        return result;
    }
}
