package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.supply.SupplyAccessManager;
import id.menki.cdrmoonfishing.supply.SupplyShopManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class SupplyListener implements Listener {
    private final SupplyShopManager shop;
    private final SupplyAccessManager access;

    public SupplyListener(SupplyShopManager shop, SupplyAccessManager access) {
        this.shop = shop;
        this.access = access;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (access.handleEntityInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof SupplyShopManager.SupplyHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;
        shop.handleClick(player, rawSlot);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof SupplyShopManager.SupplyHolder) {
            event.setCancelled(true);
        }
    }
}
