package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.market.MarketAccessManager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEntityEvent;

public final class MarketNpcListener implements Listener {
    private final MarketAccessManager marketAccess;

    public MarketNpcListener(MarketAccessManager marketAccess) {
        this.marketAccess = marketAccess;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onNpcInteract(PlayerInteractEntityEvent event) {
        if (marketAccess.handleEntityInteract(event.getPlayer(), event.getRightClicked())) {
            event.setCancelled(true);
        }
    }
}
