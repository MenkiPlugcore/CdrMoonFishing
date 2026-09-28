package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class FishDexCommand implements CommandExecutor, TabCompleter {
    private static final int PAGE_SIZE = 45;
    private final CdrMoonFishing plugin;

    public FishDexCommand(CdrMoonFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("FishDex GUI is player-only.", NamedTextColor.RED));
            return true;
        }

        int page = 1;
        if (args.length >= 1) {
            try {
                page = Math.max(1, Integer.parseInt(args[0]));
            } catch (NumberFormatException ex) {
                sender.sendMessage(Component.text("Page must be a number.", NamedTextColor.RED));
                return true;
            }
        }
        plugin.getFishingUiManager().openFishDex(player, page);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) return List.of();
        int fishCount = plugin.getFishRegistry().definitions().size();
        int maxPage = Math.max(1, (int) Math.ceil(fishCount / (double) PAGE_SIZE));
        String prefix = args[0];
        List<String> pages = new ArrayList<>();
        for (int page = 1; page <= maxPage; page++) {
            String value = Integer.toString(page);
            if (value.startsWith(prefix)) pages.add(value);
        }
        return pages;
    }
}
