package id.menki.cdrmoonfishing.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class FishMarketHolder implements InventoryHolder {
    @Override
    public @NotNull Inventory getInventory() {
        throw new UnsupportedOperationException("FishMarketHolder is marker-only.");
    }
}
