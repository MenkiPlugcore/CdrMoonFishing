package id.menki.cdrmoonfishing.bait;

import id.menki.cdrmoonfishing.model.BaitDefinition;
import id.menki.cdrmoonfishing.registry.BaitRegistry;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class BaitManager {
    private final BaitRegistry registry;
    private final Map<UUID, String> selected = new ConcurrentHashMap<>();

    public BaitManager(BaitRegistry registry) {
        this.registry = registry;
    }

    public boolean trySelect(Player player, ItemStack item) {
        BaitDefinition bait = registry.identify(item);
        if (bait == null) {
            return false;
        }
        selected.put(player.getUniqueId(), bait.id());
        player.sendActionBar(Component.text("Selected bait: ", NamedTextColor.GRAY)
                .append(Component.text(bait.displayName(), NamedTextColor.GOLD))
                .append(Component.text(" (" + registry.count(player, bait) + " available)", NamedTextColor.DARK_GRAY)));
        return true;
    }

    public boolean select(Player player, String id) {
        BaitDefinition bait = registry.get(id);
        if (bait == null || registry.count(player, bait) <= 0) {
            return false;
        }
        selected.put(player.getUniqueId(), bait.id());
        return true;
    }

    public BaitDefinition resolveSelected(Player player) {
        String id = selected.get(player.getUniqueId());
        if (id == null) {
            return null;
        }

        BaitDefinition bait = registry.get(id);
        if (bait == null || registry.count(player, bait) <= 0) {
            selected.remove(player.getUniqueId());
            player.sendActionBar(Component.text("Selected bait is out of stock. Fishing without bait.", NamedTextColor.YELLOW));
            return null;
        }
        return bait;
    }

    public boolean consume(Player player, BaitDefinition bait) {
        return registry.consumeOne(player, bait);
    }

    public String selectedId(Player player) {
        return selected.get(player.getUniqueId());
    }

    public BaitDefinition selected(Player player) {
        return registry.get(selectedId(player));
    }

    public void clearSelection(Player player) {
        selected.remove(player.getUniqueId());
    }

    public BaitRegistry registry() {
        return registry;
    }
}
