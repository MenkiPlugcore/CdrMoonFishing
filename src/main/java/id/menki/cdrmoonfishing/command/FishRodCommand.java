package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.model.RodTierDefinition;
import id.menki.cdrmoonfishing.rod.RodManager;
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

    public FishRodCommand(RodManager rodManager) {
        this.rodManager = rodManager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("info")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("Use /fishrod give <player> [tier] or /fishrod tiers.");
                return true;
            }
            showInfo(player);
            return true;
        }

        if (args[0].equalsIgnoreCase("tiers")) {
            sender.sendMessage(Component.text("Fishing Rod Tiers", NamedTextColor.AQUA));
            for (RodTierDefinition tier : rodManager.registry().tiers()) {
                sender.sendMessage(Component.text("• " + tier.id() + " — " + tier.displayName()
                        + " • " + tier.minXp() + " XP"
                        + String.format(Locale.US, " • reel %.2fx • luck +%.0f%%", tier.reelMultiplier(), tier.rarityLuck() * 100.0), NamedTextColor.GRAY));
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("give")) {
            if (!sender.hasPermission("cdrmoonfishing.admin")) {
                sender.sendMessage(Component.text("You do not have permission.", NamedTextColor.RED));
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage("Usage: /fishrod give <player> [tier]");
                return true;
            }

            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage(Component.text("Player is not online.", NamedTextColor.RED));
                return true;
            }

            String tierId = args.length >= 3 ? args[2] : null;
            if (tierId != null && rodManager.registry().get(tierId) == null) {
                sender.sendMessage(Component.text("Unknown rod tier: " + tierId, NamedTextColor.RED));
                return true;
            }

            ItemStack rod = rodManager.createRod(tierId);
            var leftovers = target.getInventory().addItem(rod);
            leftovers.values().forEach(item -> target.getWorld().dropItemNaturally(target.getLocation(), item));
            RodTierDefinition tier = rodManager.tier(rod);
            sender.sendMessage(Component.text("Gave " + target.getName() + " a " + (tier == null ? "progression rod" : tier.displayName()) + ".", NamedTextColor.GREEN));
            target.sendMessage(Component.text("You received a CdrMoonFishing progression rod.", NamedTextColor.AQUA));
            return true;
        }

        sender.sendMessage("Usage: /fishrod [info|tiers|give]");
        return true;
    }

    private void showInfo(Player player) {
        ItemStack rod = player.getInventory().getItemInMainHand();
        if (!rodManager.isProgressionRod(rod)) {
            player.sendMessage(Component.text("Hold a CdrMoonFishing progression rod in your main hand.", NamedTextColor.GRAY));
            return;
        }

        RodTierDefinition tier = rodManager.tier(rod);
        if (tier == null) {
            player.sendMessage(Component.text("This progression rod has an invalid tier.", NamedTextColor.RED));
            return;
        }

        int xp = rodManager.xp(rod);
        RodTierDefinition next = rodManager.registry().nextTier(tier);
        player.sendMessage(Component.text("━━━━━━━━ FISHING ROD ━━━━━━━━", NamedTextColor.DARK_AQUA));
        player.sendMessage(Component.text("Tier: " + tier.displayName(), NamedTextColor.AQUA));
        player.sendMessage(Component.text("XP: " + xp + (next == null ? " • MAX" : " / " + next.minXp()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(String.format(Locale.US, "Reel Power: %.2fx", tier.reelMultiplier()), NamedTextColor.GRAY));
        player.sendMessage(Component.text(String.format(Locale.US, "Rarity Luck: +%.0f%%", tier.rarityLuck() * 100.0), NamedTextColor.GRAY));
        player.sendMessage(Component.text(String.format(Locale.US, "Rod XP Gain: %.2fx", tier.xpMultiplier()), NamedTextColor.GRAY));
        if (next != null) player.sendMessage(Component.text("Next Tier: " + next.displayName(), NamedTextColor.DARK_GRAY));
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1) return filter(List.of("info", "tiers", "give"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            return filter(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(), args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            return filter(rodManager.registry().tiers().stream().map(RodTierDefinition::id).toList(), args[2]);
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
