package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.fishing.FishingSession;
import id.menki.cdrmoonfishing.model.BaitDefinition;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

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
            sender.sendMessage(Component.text("/" + label + " bait [id|none]", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " debug", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " cancel", NamedTextColor.GRAY));
            if (sender.hasPermission("cdrmoonfishing.admin")) {
                sender.sendMessage(Component.text("/" + label + " givebait <player> <id> [amount]", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/" + label + " status", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "bait" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command is player-only.", NamedTextColor.RED));
                    return true;
                }
                if (args.length == 1) {
                    BaitDefinition bait = plugin.getBaitManager().selected(player);
                    if (bait == null) {
                        sender.sendMessage(Component.text("Selected bait: NONE", NamedTextColor.GRAY));
                    } else {
                        int count = plugin.getBaitRegistry().count(player, bait);
                        sender.sendMessage(Component.text("Selected bait: " + bait.displayName() + " (" + count + ")", NamedTextColor.GOLD));
                    }
                    sender.sendMessage(Component.text("Tip: right-click a bait item to select it.", NamedTextColor.DARK_GRAY));
                    return true;
                }
                if (args[1].equalsIgnoreCase("none")) {
                    plugin.getBaitManager().clearSelection(player);
                    sender.sendMessage(Component.text("Bait selection cleared.", NamedTextColor.GREEN));
                    return true;
                }
                if (plugin.getBaitManager().select(player, args[1])) {
                    BaitDefinition bait = plugin.getBaitRegistry().get(args[1]);
                    sender.sendMessage(Component.text("Selected bait: " + bait.displayName(), NamedTextColor.GOLD));
                } else {
                    sender.sendMessage(Component.text("You do not have that bait, or the bait ID is invalid.", NamedTextColor.RED));
                }
                return true;
            }
            case "givebait" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Usage: /" + label + " givebait <player> <id> [amount]", NamedTextColor.RED));
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                BaitDefinition bait = plugin.getBaitRegistry().get(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player is not online.", NamedTextColor.RED));
                    return true;
                }
                if (bait == null) {
                    sender.sendMessage(Component.text("Unknown bait ID.", NamedTextColor.RED));
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try {
                        amount = Math.max(1, Math.min(64, Integer.parseInt(args[3])));
                    } catch (NumberFormatException ignored) {
                        sender.sendMessage(Component.text("Amount must be a number.", NamedTextColor.RED));
                        return true;
                    }
                }
                ItemStack item = plugin.getBaitRegistry().createItem(bait, amount);
                Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
                leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
                sender.sendMessage(Component.text("Gave " + amount + "x " + bait.displayName() + " to " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
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
                        + " | Prepared: " + plugin.getFishingManager().preparedCount()
                        + " | Baits: " + plugin.getBaitRegistry().definitions().size(), NamedTextColor.GRAY));
                return true;
            }
            case "debug" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("This command is player-only.", NamedTextColor.RED));
                    return true;
                }
                FishingSession session = plugin.getFishingManager().session(player);
                if (session == null) {
                    BaitDefinition bait = plugin.getBaitManager().selected(player);
                    sender.sendMessage(Component.text("No active fishing session. Prepared encounter: "
                            + plugin.getFishingManager().hasPrepared(player), NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("Selected bait: " + (bait == null ? "NONE" : bait.id()), NamedTextColor.GRAY));
                    return true;
                }
                sender.sendMessage(Component.text("Fish: " + session.fish().id()
                        + " | Behavior: " + session.fish().behavior().name()
                        + " | Bait: " + (session.baitId() == null ? "NONE" : session.baitId()), NamedTextColor.AQUA));
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
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("bait", "debug", "cancel"));
            if (sender.hasPermission("cdrmoonfishing.admin")) {
                options.add("givebait");
                options.add("status");
                options.add("reload");
            }
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (args[0].equalsIgnoreCase("bait") && args.length == 2) {
            List<String> options = new ArrayList<>(plugin.getBaitRegistry().definitions().keySet());
            options.add("none");
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return options.stream().filter(option -> option.startsWith(prefix)).toList();
        }

        if (sender.hasPermission("cdrmoonfishing.admin") && args[0].equalsIgnoreCase("givebait")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return plugin.getServer().getOnlinePlayers().stream()
                        .map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix))
                        .toList();
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.getBaitRegistry().definitions().keySet().stream()
                        .filter(id -> id.startsWith(prefix))
                        .toList();
            }
            if (args.length == 4) {
                return List.of("1", "8", "16", "32", "64");
            }
        }

        return List.of();
    }
}
