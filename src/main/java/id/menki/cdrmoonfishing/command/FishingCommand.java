package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.fishing.FishingSession;
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
import java.util.Map;

public final class FishingCommand implements CommandExecutor, TabCompleter {
    private final CdrMoonFishing plugin;

    public FishingCommand(CdrMoonFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("CdrMoonFishing v" + plugin.getPluginMeta().getVersion(), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("/" + label + " debug", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " cancel", NamedTextColor.GRAY));
            if (sender.hasPermission("cdrmoonfishing.admin")) {
                sender.sendMessage(Component.text("/" + label + " status", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reload" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("CdrMoonFishing reloaded.", NamedTextColor.GREEN));
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("CdrMoonFishing integrations", NamedTextColor.AQUA));
                for (Map.Entry<String, Boolean> entry : plugin.getIntegrationManager().status().entrySet()) {
                    sender.sendMessage(Component.text("- " + entry.getKey() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(entry.getValue() ? "DETECTED" : "OFFLINE",
                                    entry.getValue() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
                }
                sender.sendMessage(Component.text("Active sessions: " + plugin.getFishingManager().activeCount()
                        + " | Prepared: " + plugin.getFishingManager().preparedCount(), NamedTextColor.GRAY));
                return true;
            }
            case "debug" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command is player-only.", NamedTextColor.RED));
                    return true;
                }
                FishingSession session = plugin.getFishingManager().session(player);
                if (session == null) {
                    sender.sendMessage(Component.text("No active fishing session. Prepared encounter: "
                            + plugin.getFishingManager().hasPrepared(player), NamedTextColor.GRAY));
                    return true;
                }
                sender.sendMessage(Component.text("Fish: " + session.fish().id(), NamedTextColor.AQUA));
                sender.sendMessage(Component.text(String.format(Locale.US,
                        "Tension: %.1f | Progress: %.1f | Depth: %d | Region: %s",
                        session.tension(), session.progress(), session.depth(), session.region()), NamedTextColor.GRAY));
                return true;
            }
            case "cancel" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command is player-only.", NamedTextColor.RED));
                    return true;
                }
                plugin.getFishingManager().cancel(player, true);
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Unknown subcommand.", NamedTextColor.RED));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        List<String> options = new ArrayList<>(List.of("debug", "cancel"));
        if (sender.hasPermission("cdrmoonfishing.admin")) {
            options.add("status");
            options.add("reload");
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(prefix)).toList();
    }
}
