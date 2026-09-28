package id.menki.cdrmoonfishing.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class FishMarketHolder implements InventoryHolder {
    @Override
    @SuppressWarnings("DataFlowIssue")
    public @NotNull Inventory getInventory() {
        // Marker holder used only to identify Fish Market views.
        return null;
    }
}
