package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import id.menki.cdrmoonfishing.fishing.FishingSession;
import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.model.FishRarity;
import id.menki.cdrmoonfishing.stats.PlayerStatsManager.StatsSnapshot;
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

    public FishingCommand(CdrMoonFishing plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(Component.text("CdrMoonFishing v" + plugin.getPluginMeta().getVersion(), NamedTextColor.AQUA));
            sender.sendMessage(Component.text("Gunakan /fish untuk membuka menu utama.", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/fishdex [halaman]", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " stats [player]", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " bait [id|none]", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " debug", NamedTextColor.GRAY));
            sender.sendMessage(Component.text("/" + label + " cancel", NamedTextColor.GRAY));
            if (sender.hasPermission("cdrmoonfishing.admin")) {
                sender.sendMessage(Component.text("/" + label + " givebait <player> <id> [jumlah]", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/" + label + " status", NamedTextColor.GRAY));
                sender.sendMessage(Component.text("/" + label + " reload", NamedTextColor.GRAY));
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "stats" -> {
                Player target;
                if (args.length >= 2) {
                    if (!sender.hasPermission("cdrmoonfishing.admin")) {
                        sender.sendMessage(Component.text("Kamu tidak punya izin untuk melihat statistik player lain.", NamedTextColor.RED));
                        return true;
                    }
                    target = plugin.getServer().getPlayerExact(args[1]);
                    if (target == null) {
                        sender.sendMessage(Component.text("Player sedang tidak online.", NamedTextColor.RED));
                        return true;
                    }
                } else if (sender instanceof Player player) target = player;
                else {
                    sender.sendMessage(Component.text("Penggunaan: /" + label + " stats <player>", NamedTextColor.RED));
                    return true;
                }
                sendStats(sender, target);
                return true;
            }
            case "bait" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Perintah ini hanya untuk player.", NamedTextColor.RED));
                    return true;
                }
                if (args.length == 1) {
                    BaitDefinition bait = plugin.getBaitManager().selected(player);
                    if (bait == null) sender.sendMessage(Component.text("Umpan dipilih: TIDAK ADA", NamedTextColor.GRAY));
                    else {
                        int count = plugin.getBaitRegistry().count(player, bait);
                        sender.sendMessage(Component.text("Umpan dipilih: " + bait.displayName() + " (" + count + ")", NamedTextColor.GOLD));
                    }
                    sender.sendMessage(Component.text("Tips: klik kanan item umpan untuk memilihnya.", NamedTextColor.DARK_GRAY));
                    return true;
                }
                if (args[1].equalsIgnoreCase("none")) {
                    plugin.getBaitManager().clearSelection(player);
                    sender.sendMessage(Component.text("Pilihan umpan dihapus.", NamedTextColor.GREEN));
                    return true;
                }
                if (plugin.getBaitManager().select(player, args[1])) {
                    BaitDefinition bait = plugin.getBaitRegistry().get(args[1]);
                    sender.sendMessage(Component.text("Umpan dipilih: " + bait.displayName(), NamedTextColor.GOLD));
                } else sender.sendMessage(Component.text("Kamu tidak punya umpan itu, atau ID umpannya tidak valid.", NamedTextColor.RED));
                return true;
            }
            case "givebait" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("Kamu tidak punya izin.", NamedTextColor.RED));
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage(Component.text("Penggunaan: /" + label + " givebait <player> <id> [jumlah]", NamedTextColor.RED));
                    return true;
                }
                Player target = plugin.getServer().getPlayerExact(args[1]);
                BaitDefinition bait = plugin.getBaitRegistry().get(args[2]);
                if (target == null) {
                    sender.sendMessage(Component.text("Player sedang tidak online.", NamedTextColor.RED));
                    return true;
                }
                if (bait == null) {
                    sender.sendMessage(Component.text("ID umpan tidak dikenal.", NamedTextColor.RED));
                    return true;
                }
                int amount = 1;
                if (args.length >= 4) {
                    try { amount = Math.max(1, Math.min(64, Integer.parseInt(args[3]))); }
                    catch (NumberFormatException ignored) {
                        sender.sendMessage(Component.text("Jumlah harus berupa angka.", NamedTextColor.RED));
                        return true;
                    }
                }
                ItemStack item = plugin.getBaitRegistry().createItem(bait, amount);
                Map<Integer, ItemStack> leftovers = target.getInventory().addItem(item);
                leftovers.values().forEach(leftover -> target.getWorld().dropItemNaturally(target.getLocation(), leftover));
                sender.sendMessage(Component.text("Memberikan " + amount + "x " + bait.displayName() + " kepada " + target.getName() + ".", NamedTextColor.GREEN));
                return true;
            }
            case "reload" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("Kamu tidak punya izin.", NamedTextColor.RED));
                    return true;
                }
                plugin.reloadPlugin();
                sender.sendMessage(Component.text("CdrMoonFishing berhasil dimuat ulang.", NamedTextColor.GREEN));
                return true;
            }
            case "status" -> {
                if (!sender.hasPermission("cdrmoonfishing.admin")) {
                    sender.sendMessage(Component.text("Kamu tidak punya izin.", NamedTextColor.RED));
                    return true;
                }
                sender.sendMessage(Component.text("Integrasi CdrMoonFishing", NamedTextColor.AQUA));
                for (Map.Entry<String, Boolean> entry : plugin.getIntegrationManager().status().entrySet()) {
                    sender.sendMessage(Component.text("- " + entry.getKey() + ": ", NamedTextColor.GRAY)
                            .append(Component.text(entry.getValue() ? "TERDETEKSI" : "OFFLINE",
                                    entry.getValue() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
                }
                sender.sendMessage(Component.text("Sesi aktif: " + plugin.getFishingManager().activeCount()
                        + " | Disiapkan: " + plugin.getFishingManager().preparedCount()
                        + " | Umpan: " + plugin.getBaitRegistry().definitions().size()
                        + " | Ikan: " + plugin.getFishRegistry().definitions().size()
                        + " | Profil cache: " + plugin.getPlayerStatsManager().cachedProfiles(), NamedTextColor.GRAY));
                return true;
            }
            case "debug" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Perintah ini hanya untuk player.", NamedTextColor.RED));
                    return true;
                }
                FishingSession session = plugin.getFishingManager().session(player);
                if (session == null) {
                    BaitDefinition bait = plugin.getBaitManager().selected(player);
                    sender.sendMessage(Component.text("Tidak ada sesi memancing aktif. Encounter disiapkan: "
                            + plugin.getFishingManager().hasPrepared(player), NamedTextColor.GRAY));
                    sender.sendMessage(Component.text("Umpan dipilih: " + (bait == null ? "TIDAK ADA" : bait.id()), NamedTextColor.GRAY));
                    return true;
                }
                sender.sendMessage(Component.text("Ikan: " + session.fish().id()
                        + " | Perilaku: " + session.fish().behavior().displayName()
                        + " | Umpan: " + (session.baitId() == null ? "TIDAK ADA" : session.baitId()), NamedTextColor.AQUA));
                sender.sendMessage(Component.text(String.format(Locale.US,
                        "Tegangan: %.1f | Progres: %.1f | Kedalaman: %d | Wilayah: %s",
                        session.tension(), session.progress(), session.depth(), session.region()), NamedTextColor.GRAY));
                return true;
            }
            case "cancel" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(Component.text("Perintah ini hanya untuk player.", NamedTextColor.RED));
                    return true;
                }
                plugin.getFishingManager().cancel(player, true);
                return true;
            }
            default -> {
                sender.sendMessage(Component.text("Subcommand tidak dikenal.", NamedTextColor.RED));
                return true;
            }
        }
    }

    private void sendStats(CommandSender sender, Player target) {
        StatsSnapshot stats = plugin.getPlayerStatsManager().snapshot(target);
        int totalSpecies = plugin.getFishRegistry().definitions().size();
        double completion = totalSpecies == 0 ? 0.0 : stats.discoveredSpecies() * 100.0 / totalSpecies;

        sender.sendMessage(Component.text("━━━━━━━━ STATISTIK MEMANCING: " + target.getName() + " ━━━━━━━━", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Total tangkapan: " + stats.totalCatches(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(String.format(Locale.US, "Total berat: %.2f kg", stats.totalWeight()), NamedTextColor.GRAY));
        sender.sendMessage(Component.text(String.format(Locale.US,
                "FishDex: %d/%d ditemukan (%.1f%%)", stats.discoveredSpecies(), totalSpecies, completion), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Tangkapan Legendaris: " + stats.legendaryCatches(), NamedTextColor.GOLD));

        if (stats.biggestWeight() > 0.0) {
            String name = stats.biggestFishName() == null ? stats.biggestFishId() : stats.biggestFishName();
            sender.sendMessage(Component.text(String.format(Locale.US,
                    "Tangkapan terbesar: %s • %.2f kg", name, stats.biggestWeight()), NamedTextColor.GREEN));
        } else sender.sendMessage(Component.text("Tangkapan terbesar: belum ada", NamedTextColor.DARK_GRAY));

        Component rarityLine = Component.text("Kelangkaan: ", NamedTextColor.GRAY);
        for (FishRarity rarity : FishRarity.values()) {
            int count = stats.rarityCounts().getOrDefault(rarity, 0);
            rarityLine = rarityLine.append(Component.text(rarity.displayName() + " " + count + "  ", rarityColor(rarity)));
        }
        sender.sendMessage(rarityLine);
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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> options = new ArrayList<>(List.of("bait", "stats", "debug", "cancel"));
            if (sender.hasPermission("cdrmoonfishing.admin")) {
                options.add("givebait"); options.add("status"); options.add("reload");
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
        if (sender.hasPermission("cdrmoonfishing.admin") && args[0].equalsIgnoreCase("stats") && args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                    .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
        }
        if (sender.hasPermission("cdrmoonfishing.admin") && args[0].equalsIgnoreCase("givebait")) {
            if (args.length == 2) {
                String prefix = args[1].toLowerCase(Locale.ROOT);
                return plugin.getServer().getOnlinePlayers().stream().map(Player::getName)
                        .filter(name -> name.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
            }
            if (args.length == 3) {
                String prefix = args[2].toLowerCase(Locale.ROOT);
                return plugin.getBaitRegistry().definitions().keySet().stream().filter(id -> id.startsWith(prefix)).toList();
            }
            if (args.length == 4) return List.of("1", "8", "16", "32", "64");
        }
        return List.of();
    }
}
