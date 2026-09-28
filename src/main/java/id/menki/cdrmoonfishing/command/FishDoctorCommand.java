package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.CdrMoonFishing;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.Map;

public final class FishDoctorCommand implements CommandExecutor {
    private final CdrMoonFishing plugin;

    public FishDoctorCommand(CdrMoonFishing plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("cdrmoonfishing.admin")) {
            sender.sendMessage(Component.text("No permission.", NamedTextColor.RED));
            return true;
        }

        sender.sendMessage(Component.text("━━━━━━━━ CDRMOONFISHING DOCTOR ━━━━━━━━", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("Plugin: v" + plugin.getPluginMeta().getVersion(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Server: " + plugin.getServer().getName() + " " + plugin.getServer().getMinecraftVersion(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Java: " + System.getProperty("java.version"), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("Config version: " + plugin.getConfig().getInt("config-version", 0), NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Registries", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("- Fish: " + plugin.getFishRegistry().definitions().size(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Baits: " + plugin.getBaitRegistry().definitions().size(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Rod tiers: " + plugin.getRodRegistry().tiers().size(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Contracts: " + plugin.getContractManager().definitionCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Milestones: " + plugin.getMilestoneManager().definitionCount(), NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Runtime", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("- Active encounters: " + plugin.getFishingManager().activeCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Prepared encounters: " + plugin.getFishingManager().preparedCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Cached stats profiles: " + plugin.getPlayerStatsManager().cachedProfiles(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Tournament: " + (plugin.getTournamentManager().isActive() ? "ACTIVE" : "INACTIVE")
                + " | pending payouts " + plugin.getTournamentManager().pendingRewardCount(), NamedTextColor.GRAY));
        sender.sendMessage(Component.text("- Redeemed catch IDs: " + plugin.getFishMarketManager().redeemedCatchCount(), NamedTextColor.GRAY));

        sender.sendMessage(Component.text("Integrations", NamedTextColor.AQUA));
        for (Map.Entry<String, Boolean> entry : plugin.getIntegrationManager().status().entrySet()) {
            sender.sendMessage(Component.text("- " + entry.getKey() + ": ", NamedTextColor.GRAY)
                    .append(Component.text(entry.getValue() ? "DETECTED" : "OFFLINE",
                            entry.getValue() ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));
        }
        boolean economy = plugin.getEconomyHook().ensureReady();
        sender.sendMessage(Component.text("- Vault economy provider: " + (economy ? "READY" : "OFFLINE"),
                economy ? NamedTextColor.GREEN : NamedTextColor.YELLOW));

        boolean strict = plugin.getConfig().getBoolean("security.market.require-catch-uid", false);
        sender.sendMessage(Component.text("Security", NamedTextColor.AQUA));
        sender.sendMessage(Component.text("- Unique catch redemption ledger: ACTIVE", NamedTextColor.GREEN));
        sender.sendMessage(Component.text("- Legacy fish sale: " + (strict ? "BLOCKED" : "ALLOWED"),
                strict ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
        sender.sendMessage(Component.text("- Data folder writable: " + plugin.getDataFolder().canWrite(),
                plugin.getDataFolder().canWrite() ? NamedTextColor.GREEN : NamedTextColor.RED));

        if (plugin.getFishRegistry().definitions().isEmpty()) {
            sender.sendMessage(Component.text("WARNING: no fish definitions are loaded.", NamedTextColor.RED));
        }
        if (plugin.getConfig().getBoolean("economy.enabled", true) && !economy) {
            sender.sendMessage(Component.text("WARNING: economy is enabled but Vault provider is offline.", NamedTextColor.YELLOW));
        }
        sender.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.AQUA));
        return true;
    }
}
