package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.market.FishMarketManager;
import id.menki.cdrmoonfishing.market.MarketAccessManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FishMarketCommand implements CommandExecutor, TabCompleter {
    private final FishMarketManager market;
    private final MarketAccessManager access;

    public FishMarketCommand(FishMarketManager market, MarketAccessManager access) {
        this.market = market;
        this.access = access;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Perintah ini hanya untuk player.");
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("warp") || args[0].equalsIgnoreCase("spawn")) {
            access.teleportToMarket(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "featured" -> market.sendFeatured(player);
            case "price" -> market.sendHeldQuote(player);
            case "open" -> { if (admin(player)) market.open(player); }
            case "sellhand" -> { if (admin(player)) market.sellHeld(player); }
            case "sellall" -> { if (admin(player)) market.sellAll(player); }
            case "setspawn" -> { if (admin(player)) access.setWarp(player); }
            case "delspawn", "clearspawn" -> { if (admin(player)) access.clearWarp(player); }
            case "bind" -> { if (admin(player)) access.beginBind(player); }
            case "unbind" -> { if (admin(player)) access.beginUnbind(player); }
            case "bindings" -> {
                if (!admin(player)) return true;
                player.sendMessage(Component.text("━━━━━━━━ AKSES PASAR IKAN ━━━━━━━━", NamedTextColor.AQUA));
                player.sendMessage(Component.text("Warp: " + (access.hasWarp() ? "SUDAH DIATUR" : "BELUM DIATUR"),
                        access.hasWarp() ? NamedTextColor.GREEN : NamedTextColor.RED));
                player.sendMessage(Component.text("Citizens: " + (access.citizensAvailable() ? "SIAP" : "OFFLINE"),
                        access.citizensAvailable() ? NamedTextColor.GREEN : NamedTextColor.RED));
                player.sendMessage(Component.text("ID NPC terikat: " + (access.boundNpcIds().isEmpty()
                        ? "tidak ada" : access.boundNpcIds().toString()), NamedTextColor.GRAY));
            }
            default -> player.sendMessage(Component.text("/" + label + " [warp|price|featured]", NamedTextColor.GRAY));
        }
        return true;
    }

    private boolean admin(Player player) {
        if (player.hasPermission("cdrmoonfishing.admin")) return true;
        player.sendMessage(Component.text("Kunjungi Pedagang Ikan untuk menjual tangkapanmu.", NamedTextColor.RED));
        return false;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, String[] args) {
        if (args.length != 1) return List.of();
        List<String> options = new ArrayList<>(List.of("warp", "price", "featured"));
        if (sender.hasPermission("cdrmoonfishing.admin")) {
            options.addAll(List.of("open", "sellhand", "sellall", "setspawn", "delspawn", "bind", "unbind", "bindings"));
        }
        String prefix = args[0].toLowerCase(Locale.ROOT);
        return options.stream().filter(value -> value.startsWith(prefix)).toList();
    }
}
