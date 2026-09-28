package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.market.FishMarketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Locale;

public final class FishMarketCommand implements CommandExecutor, TabCompleter {
    private final FishMarketManager market;

    public FishMarketCommand(FishMarketManager market) {
        this.market = market;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command is player-only.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("open")) {
            market.open(player);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "price" -> market.sendHeldQuote(player);
            case "sellhand" -> market.sellHeld(player);
            case "sellall" -> market.sellAll(player);
            case "featured" -> market.sendFeatured(player);
            default -> player.sendMessage("/" + label + " [open|price|sellhand|sellall|featured]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return List.of("open", "price", "sellhand", "sellall", "featured").stream()
                .filter(value -> value.startsWith(prefix))
                .toList();
    }
}
