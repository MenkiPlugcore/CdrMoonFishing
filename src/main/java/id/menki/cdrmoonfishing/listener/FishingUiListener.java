package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.market.MarketAccessManager;
import id.menki.cdrmoonfishing.ui.FishingUiManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class FishingUiListener implements Listener {
    private static final int HUB_MARKET_SLOT = 14;

    private final FishingUiManager ui;
    private final MarketAccessManager marketAccess;

    public FishingUiListener(FishingUiManager ui, MarketAccessManager marketAccess) {
        this.ui = ui;
        this.marketAccess = marketAccess;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!ui.isManaged(event.getView().getTopInventory())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;

        int rawSlot = event.getRawSlot();
        if (rawSlot < 0 || rawSlot >= event.getView().getTopInventory().getSize()) return;

        if (event.getView().getTopInventory().getHolder() instanceof FishingUiManager.HubHolder
                && rawSlot == HUB_MARKET_SLOT) {
            marketAccess.teleportToMarket(player);
            return;
        }

        ui.handleClick(player, event.getView().getTopInventory(), rawSlot);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (ui.isManaged(event.getView().getTopInventory())) event.setCancelled(true);
    }
}
