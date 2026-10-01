package id.menki.cdrmoonfishing.market;

import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.jetbrains.annotations.NotNull;

public final class FishPriceListHolder implements InventoryHolder {
    private final int page;

    public FishPriceListHolder(int page) {
        this.page = Math.max(0, page);
    }

    public int page() {
        return page;
    }

    @Override
    @SuppressWarnings("DataFlowIssue")
    public @NotNull Inventory getInventory() {
        return null;
    }
}
