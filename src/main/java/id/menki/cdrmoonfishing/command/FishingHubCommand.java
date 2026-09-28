package id.menki.cdrmoonfishing.command;

import id.menki.cdrmoonfishing.ui.FishingUiManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public final class FishingHubCommand implements CommandExecutor {
    private final FishingUiManager ui;

    public FishingHubCommand(FishingUiManager ui) {
        this.ui = ui;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text("This command is player-only.", NamedTextColor.RED));
            return true;
        }

        ui.openHub(player);
        return true;
    }
}
