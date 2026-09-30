package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.model.RodTierDefinition;
import id.menki.cdrmoonfishing.rod.RodManager;
import id.menki.cdrmoonfishing.supply.SupplyAccessManager;
import id.menki.cdrmoonfishing.supply.SupplyShopManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class FishRodCommand implements CommandExecutor, TabCompleter {
    private final RodManager rodManager;
    private final SupplyShopManager supplyShop;
    private final SupplyAccessManager supplyAccess;

    public FishRodCommand(RodManager rodManager, SupplyShopManager supplyShop, SupplyAccessManager supplyAccess) {
        this.rodManager = rodManager;
        this.supplyShop = supplyShop;
        this.supplyAccess = supplyAccess;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Gunakan /fishrod give <player> [tier], /fishrod tiers, atau /fishrod supply.");
                return true;
            }
            showInfo(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("tiers")) {
            sender.sendMessage(Component.text("Tingkat Joran Pancing", NamedTextColor.AQUA));
            for (RodTierDefinition tier : rodManager.registry().tiers()) {
                sender.sendMessage(Component.text("• " + tier.id() + " — " + tier.displayName()
                        + String.format(Locale.US, " • tarik %.2fx • luck +%.0f%% • bite +%.0f%%",
                        tier.reelMultiplier(), tier.rarityLuck() * 100.0, tier.biteSpeed() * 100.0), NamedTextColor.GRAY));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("supply")) {
            return handleSupply(sender, label, args);
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("cdrmoonfishing.admin")) {
                sender.sendMessage(Component.text("Kamu tidak punya izin.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("Penggunaan: /fishrod give <player> [tier]");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player sedang tidak online.", NamedTextColor.RED));
                return true;
            }

            String tierId = args.length >= 3 ? args[2] : null;
            if (tierId != null && rodManager.registry().get(tierId) == null) {
                sender.sendMessage(Component.text("Tingkat joran tidak dikenal: " + tierId, NamedTextColor.RED));
                return true;
            }

            ItemStack rod = rodManager.createRod(tierId);
            var leftovers = target.getInventory().addItem(rod);
            leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            RodTierDefinition tier = rodManager.tier(rod);
            sender.sendMessage(Component.text("Memberikan " + (tier == null ? "joran CdrMoonFishing" : tier.displayName())
                    + " kepada " + target.getName() + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text("Kamu menerima joran CdrMoonFishing.", NamedTextColor.AQUA));
            return true;
        }

        sender.sendMessage("Penggunaan: /fishrod [info|tiers|give|supply]");
        return true;
    }

    private boolean handleSupply(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("cdrmoonfishing.admin")) {
            sender.sendMessage(Component.text("Fishing Supply diakses dengan klik kanan NPC pedagang.", NamedTextColor.GRAY));
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Perintah Fishing Supply harus dijalankan oleh player.");
            return true;
        }

        if (args.length == 1 || args[1].equalsIgnoreCase("open")) {
            supplyShop.open(player);
            return true;
        }

        switch (args[1].toLowerCase(Locale.ROOT)) {
            case "bind" -> supplyAccess.beginBind(player);
            case "unbind" -> supplyAccess.beginUnbind(player);
            case "reload" -> {
                supplyShop.reload();
                supplyAccess.reload();
                player.sendMessage(Component.text("Fishing Supply berhasil dimuat ulang.", NamedTextColor.GREEN));
            }
            case "status", "bindings" -> {
                player.sendMessage(Component.text("━━━━━━━━ FISHING SUPPLY ━━━━━━━━", NamedTextColor.AQUA));
                player.sendMessage(Component.text("Citizens: " + (supplyAccess.citizensAvailable() ? "SIAP" : "OFFLINE"),
                        supplyAccess.citizensAvailable() ? NamedTextColor.GREEN : NamedTextColor.RED));
                player.sendMessage(Component.text("NPC terikat: " + (supplyAccess.boundNpcIds().isEmpty()
                        ? "tidak ada" : supplyAccess.boundNpcIds().toString()), NamedTextColor.GRAY));
            }
            default -> player.sendMessage(Component.text("/" + label + " supply [open|bind|unbind|status|reload]", NamedTextColor.GRAY));
        }
        return true;
    }

    private void showInfo(Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!rodManager.isProgressionRod(rod)) {
            player.sendMessage(Component.text("Pegang joran CdrMoonFishing di tangan utama.", NamedTextColor.GRAY));
            return;
        }

        RodTierDefinition tier = rodManager.tier(rod);
        if (tier == null) {
            player.sendMessage(Component.text("Joran ini memiliki tingkat yang tidak valid.", NamedTextColor.RED));
            return;
        }

        RodTierDefinition next = rodManager.registry().nextTier(tier);
        player.sendMessage(Component.text("━━━━━━━━ JORAN PANCING ━━━━━━━━", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text("Tingkat: " + tier.displayName(), NamedTextColor.AQUA));
        player.sendMessage(Component.text(String.format(Locale.US, "Kekuatan Tarik: %.2fx", tier.reelMultiplier()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(String.format(Locale.US, "Luck Kelangkaan: +%.0f%%", tier.rarityLuck() * 100.0), NamedTextColor.GRAY));
        player.sendMessage(Component.text(String.format(Locale.US, "Kecepatan Nyamber: +%.0f%%", tier.biteSpeed() * 100.0), NamedTextColor.GRAY));
        if (next != null) {
            player.sendMessage(Component.text("Tingkat Berikutnya: " + next.displayName(), NamedTextColor.DARK_GRAY));
        } else {
            player.sendMessage(Component.text("Tingkat maksimum.", NamedTextColor.GOLD));
        }
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("info", "tiers", "give", "supply"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) return filter(rodManager.registry().tiers().stream().map(RodTierDefinition::id).toList(), args[2]);
        if (args.length == 2 && args[0].equalsIgnoreCase("supply") && sender.hasPermission("cdrmoonfishing.admin")) {
            return filter(List.of("open", "bind", "unbind", "status", "reload"), args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> source, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String value : source) if (value.toLowerCase(Locale.ROOT).startsWith(lower)) result.add(value);
        return result;
    }
}
