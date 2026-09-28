package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.market.FishMarketManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class FishMarketListener implements Listener {
    private final FishMarketManager market;

    public FishMarketListener(FishMarketManager market) {
        this.market = market;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!market.isMarket(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot >= 0 && rawSlot < event.getView().getTopInventory().getSize()) {
            market.handleClick(player, rawSlot);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (market.isMarket(event.getView().getTopInventory())) {
            event.setCancelled(true);
        }
    }
}
