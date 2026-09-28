package id.menki.cdrmoonfishing.listener;

import id.menki.cdrmoonfishing.bait.BaitManager;
import id.menki.cdrmoonfishing.fishing.FishingManager;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class FishingListener implements Listener {
    private final FishingManager fishingManager;
    private final BaitManager baitManager;

    public FishingListener(FishingManager fishingManager, BaitManager baitManager) {
        this.fishingManager = fishingManager;
        this.baitManager = baitManager;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFish(PlayerFishEvent event) {
        if (fishingManager.isActive(event.getPlayer())) {
            event.setCancelled(true);
            return;
        }

        switch (event.getState()) {
            case BITE -> fishingManager.prepareEncounter(event.getPlayer(), event.getHook().getLocation());
            case CAUGHT_FISH -> {
                event.setCancelled(true);
                event.setExpToDrop(0);
                if (event.getCaught() != null) event.getCaught().remove();
                event.getHook().remove();
                fishingManager.startPrepared(event.getPlayer(), event.getHook().getLocation());
            }
            case FAILED_ATTEMPT, REEL_IN, IN_GROUND -> fishingManager.clearPrepared(event.getPlayer());
            default -> { }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        if (baitManager.trySelect(event.getPlayer(), event.getPlayer().getInventory().getItemInMainHand())) {
            event.setCancelled(true);
            return;
        }

        if (!fishingManager.isActive(event.getPlayer())) return;
        if (event.getPlayer().getInventory().getItemInMainHand().getType() != Material.FISHING_ROD) return;

        event.setCancelled(true);
        fishingManager.reelPulse(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        fishingManager.cancel(event.getPlayer(), false);
        baitManager.clearSelection(event.getPlayer());
    }
}
